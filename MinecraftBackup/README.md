<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Minecraft on GKE Backup How-To](#minecraft-on-gke-backup-how-to)
  - [Two Different Kinds of Service Accounts](#two-different-kinds-of-service-accounts)
  - [Google Storage Bucket](#google-storage-bucket)
  - [GCP Service Account](#gcp-service-account)
  - [Kubernetes Secret](#kubernetes-secret)
  - [Backup Script](#backup-script)
  - [Docker Image](#docker-image)
  - [Kubernetes Service Account](#kubernetes-service-account)
  - [Kubernetes CronJob](#kubernetes-cronjob)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

Minecraft on GKE Backup How-To
===============================

The goal is to create a backup of a running (vanilla) Minecraft server world and store the backup file in a GS Bucket, given these conditions:

- In Google Kubernetes Engine (GKE)...
- Minecraft is deployed into Kubernetes using [this Heml
chart](https://github.com/helm/charts/tree/master/stable/minecraft)...
- in namespace `minecraft`.
- To perform a backup, a copy of the whole directory `world` must be made.
- The backup will be stored in a Google Storage (GS) Bucket



Two Different Kinds of Service Accounts
----------------------------------------

There are two different types of Service Account involved in this operation:

1. a **[Kubernetes Service Account](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/)**, needed to give your Kubernetes CronJob Pod the
ability to `kubectl exec` into your Minecraft Pod to _create_ the backup, and

1. a **[Google Compute Platform (GCP) Service Account](https://cloud.google.com/iam/docs/service-accounts)**, needed to copy the backup file to a Google Storage Bucket with `gsutil cp`.


Google Storage Bucket
----------------------

**A GS Bucket is the final resting place of your backup file.**

Create a new Google Storage Bucket, using whatever storage class is most
appropriate. I chose Multi-region _Nearline_. Access control is set at the
Bucket-level only (rather than on a per-file basis). For production usage,
also consider setting

- a retention policy (that prevents early deletion) and/or 
- lifecycle rules (to automatically delete or move very old backup files) may be
appropriate.

I did this "manually" using the [Web
UI](https://console.cloud.google.com/storage/browser).


GCP Service Account
--------------------

**A GCP Service Account is needed by `gsutil` to copy the backup file to a GS
Bucket.**

Create a new GCP Service Account with _Storage Object Creator_ permission.
You'll download a JSON file that you should stash someplace safe (i.e., where
your team can find it if you get hit by a bus but _not_ in your version control
system).

(**Question**: Can this permission be further restricted so that it is specific to
a single GS Bucket?)

I did this "manually" using the [Web
UI](https://console.cloud.google.com/iam-admin/serviceaccounts).


Kubernetes Secret
------------------

**A Kubernetes Secret is used to supply GCP Service Account Credentials to your
backup script.**

Create a Kubernetes Secret from the GCP Service Account JSON key file (my file
was named `maximal-copilot-249415-f9dd798719b2.json`).

    $ kubectl create secret generic \
        minecraft-backup-bucket-writer-gcp-svc-acct \
        --from-file=key.json=maximal-copilot-249415-f9dd798719b2.json


Backup Script
--------------

**The backup script runs in a Kubernetes Pod to create the backup file and copy
it to a GS Bucket.**

[backup_minecraft.sh](backup_minecraft.sh) is very short and heavily commented,
so go read it. 

Pay attention to the `gcloud auth activate-service-account` command at the
beginning. **`gsutil` does NOT use the `GOOGLE_APPLICATION_CREDENTIALS`
environment variables for credentials.**


Docker Image
-------------

**The Docker Image includes `kubectl`, `gsutil`, and your backup script.**

Your backup job will run as a Kubernetes _CronJob_, which periodically spawns a
Pod, does some work, then disappears until its next scheduled run. Your job
will need access to two specific command line tools:

- `kubectl`, to `exec` a command in the currently running Minecraft Pod, and
- `gsutil`, to copy the backup file to a GS Bucket.

Google's own `google/cloud-sdk` image already has these tools installed, so
you'll only need to add [the backup script](backup_minecraft.sh) to it:

```docker
FROM google/cloud-sdk
COPY backup_minecraft.sh /
CMD [ "/bin/bash", "/backup_minecraft.sh" ]
```

Build the Image...

    $ docker build -t minecraft-backup:v0.1 .
    ...
    Successfully tagged minecraft-backup:v0.1

...tag it for inclusion in Google Container Registry (GCR)...

    $ docker tag minecraft-backup:v0.1 gcr.io/maximal-copilot-249415/minecraft-backup

...and push it up to GCR...

    $ docker push gcr.io/maximal-copilot-249415/minecraft-backup
    The push refers to repository [gcr.io/maximal-copilot-249415/minecraft-backup]
    ...
    latest: digest: sha256:124acdbfba2008255f860aba18219d731b9cc42f322e00721eb44c79bc541ab3 size: 1160


Kubernetes Service Account
---------------------------

**A Kubernetes Service Account allows your script to find the Minecraft Pod,
then `kubectl exec` on it.**

We'll need a Kubernetes _Role_ that allows us to `get`, `list`, and `exec` on
Pods in the namespace...

```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
    namespace: minecraft
    name: pod-get-list-exec
rules:
    - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list"]
    - apiGroups: [""]
    resources: ["pods/exec"]
    verbs: ["create"]
```

...and a _ServiceAccount_...

```yaml
kind: ServiceAccount
apiVersion: v1
metadata:
    name: minecraft-backup-account
```

...and a _RoleBinding_ that grants the _Role_ to the _ServiceAccount_.

```yaml
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
    name: pod-get-list-exec-binding
    namespace: minecraft
roleRef:
    apiGroup: rbac.authorization.k8s.io
    kind: Role
    name: pod-get-list-exec
subjects:
    - kind: ServiceAccount
    name: minecraft-backup-account
    namespace: minecraft
```

The first three sections of
[minecraft-backup.cronjob.yaml](minecraft-backup.cronjob.yaml) describe these
resources.


Kubernetes CronJob
-------------------

Finally, we need a Kubernetes [CronJob](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/) that ties it all together.

From the [Kubernetes Engine Cronjobs
docs](https://cloud.google.com/kubernetes-engine/docs/how-to/cronjobs) (emphasis added):

> The spec.schedule field defines when, and how often, the CronJob runs, using Unix standard crontab format. **All CronJob times are in UTC.**

```yaml
kind: CronJob
apiVersion: batch/v1beta1
metadata:
    name: minecraft-backup
    namespace: minecraft
    labels:
        app: minecraft-backup
spec:
    schedule: "45 8 * * *"
    jobTemplate:
        spec:
            template:
                spec:
                    serviceAccountName: minecraft-backup-account
                    containers:
                        - name: minecraft-backup
                          image: gcr.io/maximal-copilot-249415/minecraft-backup
                          volumeMounts:
                              - name: google-cloud-key
                                mountPath: /var/secrets/google
                    restartPolicy: OnFailure
                    volumes:
                        - name: google-cloud-key
                          secret:
                              secretName: minecraft-backup-bucket-writer-gcp-svc-acct
```
