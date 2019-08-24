<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**

- [Saturday, July 13, 2019](#saturday-july-13-2019)
- [Saturday, July 20, 2019](#saturday-july-20-2019)
  - [Cassandra as a _StatefulSet_](#cassandra-as-a-_statefulset_)
- [Saturday, July 28, 2019](#saturday-july-28-2019)
- [Saturday, August 3, 2019](#saturday-august-3-2019)
  - [Manual Development Cycle](#manual-development-cycle)
  - [DNS Problems](#dns-problems)
- [Monday, August 5](#monday-august-5)
- [Saturday, August 10](#saturday-august-10)
- [Saturday, August 17, 2019](#saturday-august-17-2019)
  - [Repeat GCP Procedure](#repeat-gcp-procedure)
  - [Debugging an Application Running in Minikube](#debugging-an-application-running-in-minikube)
  - [Debugging an Application Running in GCP K8s](#debugging-an-application-running-in-gcp-k8s)
  - [Cleanup](#cleanup)
- [Saturday, August 24, 2019](#saturday-august-24-2019)
  - [Repeat GCP Procedure](#repeat-gcp-procedure-1)
  - [Consolidate Application Configuration](#consolidate-application-configuration)
  - [Cleanup](#cleanup-1)
- [Next Steps](#next-steps)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

Saturday, July 13, 2019
========================

Get the Spring "Hello, World!" container running in Minikube. The following
commands are condensed from the Kubernetes Docs' section [Hello Minikube](https://kubernetes.io/docs/tutorials/hello-minikube/).

    $ # build and create a Docker image
    $ gradle dockerClean
    $ gradle docker

    $ # create a local Kubernetes instance
    $ sudo minikube start --vm-driver=none
    $ sudo minikube dashboard
    [sudo] password for ed:
    🤔  Verifying dashboard health ...
    ... blah, blah, blah ...
    xdg-open: no method available for opening 'http://127.0.0.1:33689/api/v1/namespaces/kube-system/services/http:kubernetes-dashboard:/proxy/'
    $ # open this URL in your web browser manually --^

    $ # deploy a container image - create a Deployment
    $ sudo kubectl run hello-spring \
        --image=tzahk/spring-hello-world \
        --image-pull-policy=Never \
        --port=8080
    $ sudo kubectl get deployment
    NAME           READY   UP-TO-DATE   AVAILABLE   AGE
    hello-spring   1/1     1            1           6m22s

    $ # expose a service - create a Service
    $ sudo kubectl expose deployment hello-spring \
        --type=LoadBalancer \
        --port=8081 \
        --target-port=8080
    $ sudo kubectl get service -o wide
    NAME           TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE     SELECTOR
    hello-spring   LoadBalancer   10.105.65.170   <pending>     8081:31699/TCP   3m22s   run=hello-spring
    kubernetes     ClusterIP      10.96.0.1       <none>        443/TCP          88m     <none>

    $ firefox http://10.105.65.170:8081

    $ minikube delete


Saturday, July 20, 2019
========================

Starting with [Stateful Set
Basics](https://kubernetes.io/docs/tutorials/stateful-application/basic-stateful-set/),
prerequisites. First
[Volumes](https://kubernetes.io/docs/concepts/storage/volumes/), the most
interesting of which are _emptyDir_ (ephemeral, Pod-wide scratch space),
_local_ (maybe what I need for Cassandra in Minikube?), _configMap_ (for
injecting configuration information into a Pod), and similarly, _secret_ (for
injecting credentials and other sensitive configuration into a Pod).

> A Kubernetes Volume, on the other hand, has an explicit lifetime - the same
as the Pod that encloses it. Consequently, a Volume outlives any Containers
that run within the Pod, and data is preserved across Container restarts. Of
course, when a Pod ceases to exist, the volume will cease to exist, too.

Next, [Persistent
Volumes](https://kubernetes.io/docs/concepts/storage/persistent-volumes/) (PV),
are longer-lived, with a lifecycle associated with the entire Cluster, not just
a single Pod. A Pod uses a _PersistentVolumeClaim_ (PVC) to specify and request
a type and volume of storage. The binding between _PersistenceVolumeClaim_ and
a _PersistentVolume_ is one-to-one; neither are shared once bound together.

> **Question**: Do PV Access Mode _ReadOnlyMany_ and/or _ReadWriteMany_ relax
this 1:1 constraint?
>
> **Answer:** _PersistentVolumes_ binds are exclusive, and since
_PersistentVolumeClaims_ are namespaced objects, mounting claims with
_ReadOnlyMany_ (ROX) or _ReadWriteMany_ (RWX) is only possible within one
namespace.

A Pod uses a _PersistentVolumeClaim_ as though it was a _Volume_.

When a user no longer needs it, they can delete the PVC objects from the API
which allows reclamation of the resource. The reclaim policy for a
_PersistentVolume_ determines what will happen; it may be Retained (no data
lost, but manual work required to delete), Recycled (limited support and
deprecated in favor of Delete + dynamic provisioning) or Deleted (just what it
says on the tin).

Cassandra as a _StatefulSet_
-----------------------------

Restart Minikube with more memory and processors (but leave some for yourself).

    $ sudo minikube stop
    $ free -h
                total        used        free      shared  buff/cache   available
    Mem:            15G        3.2G        6.9G        766M        5.5G         11G
    Swap:           15G          0B         15G
    $ export CPU_COUNT=$( grep -c ^processor /proc/cpuinfo )
    $ sudo minikube start --vm-driver=none --memory 5120 --cpus=$((CPU_COUNT-1))

Create a Service to expose Cassandra (using the local YAML file or [the
original](https://raw.githubusercontent.com/kubernetes/website/master/content/en/examples/application/cassandra/cassandra-service.yaml)).

    $ sudo kubectl apply -f cassandra-service.yaml
    $ sudo kubectl get service cassandra
    NAME           TYPE           CLUSTER-IP     EXTERNAL-IP   PORT(S)          AGE
    cassandra      ClusterIP      None           <none>        9042/TCP         21m

Create a StatefulSet of Cassandra instances (using the local YAML file or [the
original](https://raw.githubusercontent.com/kubernetes/website/master/content/en/examples/application/cassandra/cassandra-statefulset.yaml)).

    $ sudo kubectl apply -f cassandra-statefulset.yaml

This may take several minutes...

    $ sudo kubectl get statefulset
    NAME        READY   AGE
    cassandra   3/3     16m

Running the following command periodically will reveal the start-up process.

    $ sudo kubectl get pods
    NAME                            READY   STATUS    RESTARTS   AGE
    cassandra-0                     1/1     Running   0          17m
    cassandra-1                     1/1     Running   0          16m
    cassandra-2                     1/1     Running   0          14m

Run the Cassandra cluster/ring status tool inside the first Pod to
confirm that the Cassandra instances have found one another.

    $ kubectl exec -it cassandra-0 -- nodetool status
    Datacenter: DC1-K8Demo
    ======================
    Status=Up/Down
    |/ State=Normal/Leaving/Joining/Moving
    --  Address     Load       Tokens  Owns (effective)  Host ID    Rack
    UN  172.17.0.7  103.81 KiB  32      69.8%             2a11b...  Rack1-K8Demo
    UN  172.17.0.6  104.55 KiB  32      62.9%             df986...  Rack1-K8Demo
    UN  172.17.0.8  65.87 KiB   32      67.4%             cd220...  Rack1-K8Demo

Now scale up the Cassandra ring by adding an instance. (In the .yaml file,
change `[0].spec.replicas` from `3` to `4`.)

    $ sudo kubectl edit statefulsets.apps cassandra
    statefulset.apps/cassandra edited

If you run the following commands immediately, you can see that Kubernetes is
moving the Cluster into the new desired state.

    $ sudo kubectl get statefulset cassandra
    NAME        READY   AGE
    cassandra   3/4     28m

    $ sudo kubectl get pods
    NAME                            READY   STATUS    RESTARTS   AGE
    cassandra-0                     1/1     Running   0          28m
    cassandra-1                     1/1     Running   0          27m
    cassandra-2                     1/1     Running   0          25m
    cassandra-3                     0/1     Running   0          49s
    hello-spring-684c5969d9-q5k8g   1/1     Running   1          174m

Once all the Pods are ready, confirm that Cassandra has found its new member.

    $ kubectl exec -it cassandra-0 -- nodetool status

Delete the _StatefulSet_ and _PersistentVolumeClaims_.

    $ grace=$(kubectl get po cassandra-0 \
            -o=jsonpath='{.spec.terminationGracePeriodSeconds}') \
        && kubectl delete statefulset -l app=cassandra \
        && echo "Sleeping $grace" \
        && sleep $grace \
        && kubectl delete pvc -l app=cassandra

Finally, delete the _Service_.

    $ sudo kubectl delete service cassandra

And confirm that they're all gone:

    $ sudo kubectl get pvc
    No resources found.
    
    $ sudo kubectl get statefulsets.apps 
    No resources found.
     
    $ sudo kubectl get service cassandra
    Error from server (NotFound): services "cassandra" not found


Saturday, July 28, 2019
========================

Because I use Maven at work, I decided to take a little detour and figure out
how to manage dependencies and the build process using it (instead of Gradle,
which this example originally used.)

I built a stand-alone, Maven-managed project based on [this Datastax 4.x driver
example](https://github.com/datastax/java-driver/blob/4.x/examples/src/main/java/com/datastax/oss/driver/examples/basic/ReadCassandraVersion.java),
the [Datastax 4.1 Javadocs](https://docs.datastax.com/en/drivers/java/4.1/),
[human docs](https://docs.datastax.com/en/developer/java-driver/4.1/) (which
aren't great) and the [Datastax 4.1 driver in Apache's Maven
repository](http://repo.maven.apache.org/maven2/com/datastax/oss/java-driver-core/4.1.0/).

The [Maven Getting Started
Guide](https://maven.apache.org/guides/getting-started/index.html) was very
useful, and linked to many other concept introductions.


Saturday, August 3, 2019
========================

[Install Cassandra](http://cassandra.apache.org/download/) for the command line
tools and [disable the Cassandra server via `sudo systemctl disable
cassandra`](https://askubuntu.com/questions/19320/how-to-enable-or-disable-services).

    $ sudo minikube start --vm-driver=none
    ...

    $ sudo minikube dashboard
    ...

    $ kubectl get pods
    NAME                            READY   STATUS    RESTARTS   AGE
    cassandra-0                     1/1     Running   1          7d1h
    hello-spring-684c5969d9-q5k8g   1/1     Running   3          13d

    $ kubectl port-forward cassandra-0 9042
    Forwarding from 127.0.0.1:9042 -> 9042
    Forwarding from [::1]:9042 -> 9042

    $ cqlsh
    Connected to K8Demo at 127.0.0.1:9042.
    [cqlsh 5.0.1 | Cassandra 3.11.2 | CQL spec 3.4.4 | Native protocol v4]
    Use HELP for help.
    cqlsh> describe keyspaces

    system_traces  system_schema  system_auth  system  system_distributed

Started a new Spring Boot application using Maven this time using [this
how-to](https://spring.io/guides/gs/spring-boot/).

Learned how to "manually" build and run the Spring Boot application as a Docker
container (outside of Kubernetes) using a very simple `Dockerfile`:

    FROM adoptopenjdk/openjdk11:alpine-jre
    COPY target/service-that-logs-0.1.0.jar .
    EXPOSE 8080/tcp
    ENTRYPOINT ["java","-jar","service-that-logs-0.1.0.jar"]

It took a little digging, but I found a [JDK 11 + Alpine Docker base
image](https://hub.docker.com/r/adoptopenjdk/openjdk11) that's a (relatively)
lean 141 MB, compared to the ~250 MB `openjdk/11-jre` image and the portly 600+
MB `openjdk/11`.

Other examples seem really interested in `COPY`ing their build artifacts in
other directories (e.g., `/app/lib`, or `/usr/src`); I'm not sure why. Just
putting it in the working directory seems to work OK.

Then, with the help of 
- https://docs.docker.com/get-started/part2/#build-the-app
- https://docs.docker.com/engine/reference/commandline/run/#publish-or-expose-port

I ran

    $ docker build --tag=service-that-logs:0.1.0 .
    
    $ docker run -p 127.0.0.1:80:8080/tcp service-that-logs:0.1.0

I added environment variable control of the log level, using this [article on
environment variables, application.properties, and
Spring](https://blog.indrek.io/articles/using-environment-variables-with-spring-boot/),
this [tip on request detail
logging](https://stackoverflow.com/questions/53723613/how-to-set-enableloggingrequestdetails-true-in-spring-boot),
[a little refresher on environment variable
scope](https://askubuntu.com/a/205698/398512), an [exhaustive list of Spring
Boot configuration
overriding](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html),
which all started with [Sping Boot Logback
configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto-logging.html#howto-configure-logback-for-logging).

Now, it's finally time to use the Maven-built, Docker Container-ized, Spring
Boot application in Kubernetes!

    $ # deploy a container image
    $ kubectl run service-that-logs \
        --image=service-that-logs:0.1.0 \
        --image-pull-policy=Never \
        --port=8080 \
        --env="LOG_LEVEL=DEBUG"

    $ sudo kubectl get deployment

    $ # expose the Deployment
    $ kubectl expose deployment service-that-logs \
        --type=LoadBalancer \
        --port=8081 \
        --target-port=8080

    $ kubectl get service -o wide
    NAME                TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE     SELECTOR
    cassandra           ClusterIP      None            <none>        9042/TCP         7d16h   app=cassandra
    kubernetes          ClusterIP      10.96.0.1       <none>        443/TCP          20d     <none>
    service-that-logs   LoadBalancer   10.106.190.55   <pending>     8081:30622/TCP   7s      run=service-that-logs

    $ # contact the service using the Cluster-internal IP address
    $ curl 10.106.190.55:8081 && echo
    Hello, curl/7.58.0!

    $ # contact the service using the external IP address and port
    $ curl localhost:30622 && echo
    Hello, curl/7.58.0!

You _can_ view the logs using Docker, only bceause we're using Minikube:

    $ # find the Container ID
    $ docker ps
    ...
    $ # follow (-f) the container logs
    $ docker logs -f d623c865464c

Alternatively (and more correctly) you _should_:

    $ kubectl get pods
    NAME                                 READY   STATUS    RESTARTS   AGE
    cassandra-0                          1/1     Running   2          7d16h
    service-that-logs-7d8579b587-5vz55   1/1     Running   0          16m

    $ kubectl logs -f service-that-logs-7d8579b587-5vz55

Manual Development Cycle
-------------------------

Now, let's make a change to the application and redeploy it (omitting command
output).  We'll increate the "version number" embedded in the Docker image tag,
just to differentiate between the old and new images.

    $ kubectl delete service service-that-logs

    $ kubectl delete deployment service-that-logs

    $ # change the source code to say "Howdy" instead of "Hello"

    $ mvn package

    $ docker build --tag=service-that-logs:0.1.1 .

    $ kubectl run service-that-logs \
        --image=service-that-logs:0.1.1 \
        --image-pull-policy=Never \
        --port=8080 \
        --env="LOG_LEVEL=DEBUG"

    $ kubectl expose deployment service-that-logs \
        --type=LoadBalancer \
        --port=8081 \
        --target-port=8080  

    $ kubectl get services
    NAME                TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
    cassandra           ClusterIP      None             <none>        9042/TCP         7d16h
    kubernetes          ClusterIP      10.96.0.1        <none>        443/TCP          20d
    service-that-logs   LoadBalancer   10.107.119.207   <pending>     8081:32033/TCP   111s

    $ curl localhost:32033
    Howdy, curl/7.58.0!

The [run_k8s_service.sh](run_k8s_service.sh) script describes this process in
more detail, and prevents me having to use a more sophisticated setup until I
get my application to talk to Cassandra.

DNS Problems
-------------

[Kubernetes DNS Troubleshooting Guide](https://kubernetes.io/docs/tasks/administer-cluster/dns-debugging-resolution/)

The DNS service in my K8s cluster is not running (properly):

    $ kubectl get pods --namespace=kube-system
    NAME                                    READY   STATUS             RESTARTS   AGE
    coredns-5c98db65d4-9mxkq                0/1     CrashLoopBackOff   65         21d
    coredns-5c98db65d4-pc2vm                0/1     CrashLoopBackOff   65         21d
    etcd-minikube                           1/1     Running            5          21d
    kube-addon-manager-minikube             1/1     Running            5          21d
    ...

Notice the _READY_, _STATUS_, and _RESTARTS_ values for `coredns`. The logs for
one such crashed pod pointed me in the right direction:

    $ kubectl -n kube-system logs -f coredns-5c98db65d4-9mxkq
    .:53
    2019-08-04T00:33:19.719Z [INFO] CoreDNS-1.3.1
    2019-08-04T00:33:19.719Z [INFO] linux/amd64, go1.11.4, 6b56a9c
    CoreDNS-1.3.1
    linux/amd64, go1.11.4, 6b56a9c
    2019-08-04T00:33:19.719Z [INFO] plugin/reload: Running configuration MD5 = 5d5369fbc12f985709b924e721217843
    2019-08-04T00:33:19.720Z [FATAL] plugin/loop: Loop (127.0.0.1:59785 -> :53) detected for zone ".", see https://coredns.io/plugins/loop#troubleshooting. Query: "HINFO 5223451210285519832.6127911068346062510."

[This "hacky solution" seemed to work](https://stackoverflow.com/a/53414041/150),
although I tried the "preferred solution without luck (I couldn't find the loop
in my host system's resolv config).

> Edit the CoreDNS configmap:
>
>       kubectl -n kube-system edit configmap coredns
>
> Remove or comment out the line with "loop", save and exit.
> 
> Then remove the CoreDNS pods, so new ones can be created with new config:
>
>       kubectl -n kube-system delete pod -l k8s-app=kube-dns

Now, things look a little better, but the inability to resolve
`kubernetes.default` still worries me a little.

```yaml
    $ kubectl exec -it busybox -- nslookup kubernetes.default
    Server:    10.96.0.10
    Address 1: 10.96.0.10 kube-dns.kube-system.svc.cluster.local

    nslookup: can't resolve 'kubernetes.default'
    command terminated with exit code 1

    $ kubectl exec -it busybox -- nslookup cassandra
    Server:    10.96.0.10
    Address 1: 10.96.0.10 kube-dns.kube-system.svc.cluster.local

    Name:      cassandra
    Address 1: 172.17.0.3 cassandra-0.cassandra.default.svc.cluster.local
```

The Java app crashes, but with a different error:

    You provided explicit contact points, the local DC must be specified (see basic.load-balancing-policy.local-datacenter in the config)

I just guessed and set the datacenter name to "local", but that led to
`NoNodeAvailableException`s being thrown because the Cassandra driver was
trying to find some member of the "local" datacanter at the address I provided,
but that wasn't the **correct** name of the datacenter.

I needed to set the Cassandra datacenter name _correctly_ using
`CqlSessionFactory.withLocalDatacenter(<name>)`. To find it, [query the system
keyspace](https://stackoverflow.com/questions/19489498/getting-cassandra-datacenter-name-in-cqlsh).

```bash
    $ kubectl port-forward cassandra-0 9042 &

    $ cqlsh -e "select data_center from system.local;"

        data_center
        -------------
        DC1-K8Demo

        (1 rows)
```

Monday, August 5
=================

After reading about using _ConfigMap_ contents
[selectively](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#define-container-environment-variables-using-configmap-data)
or [in whole
maps](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/#configure-all-key-value-pairs-in-a-configmap-as-container-environment-variables)
to set environment variables, I started working on YAML files for
_ServiceThatLogs_.

    $ kubectl get deploy service-that-logs -o=yaml --export > service-that-logs.yaml

Got me started, then manually preparing a ConfigMap YAML...

```yaml
    apiVersion: v1
    kind: ConfigMap
    metadata:
      name: service-that-logs-config
      namespace: default
    data:
      LOG_LEVEL: DEBUG
      CASSANDRA_HOSTNAME: cassandra
      CASSANDRA_PORT: "9042"
      CASSANDRA_DATACENTER: DC1-K8Demo
```

And replacing the individual environment variables in the service YAML...

```yaml
      containers:
      - env:
        - name: LOG_LEVEL
          value: DEBUG
          ...
```

with a single "bulk" reference to the _ConfigMap_...

```yaml
      containers:
      - envFrom:
        - configMapRef:
            dname: service-that-logs-config
```

Saturday, August 10
====================

1. Signed up for GCP. Sadly, had to activate billing to try out GCP Kubernetes
Engine.

1. Created a Kubernetes Cluster using the Web UI equivalent to

    ```bash
    $ gcloud beta container \
        --project "maximal-copilot-249415" clusters create "test" \
        --zone "us-central1-a" \
        --no-enable-basic-auth \
        --cluster-version "1.12.8-gke.10" \
        --machine-type "n1-standard-1" \
        --image-type "COS" \
        --disk-type "pd-standard" \
        --disk-size "100" \
        --scopes "https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write","https://www.googleapis.com/auth/monitoring","https://www.googleapis.com/auth/servicecontrol","https://www.googleapis.com/auth/service.management.readonly","https://www.googleapis.com/auth/trace.append" \
        --num-nodes "3" \
        --enable-cloud-logging \
        --enable-cloud-monitoring \
        --enable-ip-alias \
        --network "projects/maximal-copilot-249415/global/networks/default" \
        --subnetwork "projects/maximal-copilot-249415/regions/us-central1/subnetworks/default" \
        --default-max-pods-per-node "110" \
        --addons HorizontalPodAutoscaling,HttpLoadBalancing \
        --enable-autoupgrade \
        --enable-autorepair
    ```

1. [install Google Cloud SDK on
Ubuntu](https://cloud.google.com/sdk/docs/downloads-apt-get)

1. [install kubectx](https://github.com/ahmetb/kubectx), which I've been using
at work and makes cluster and namespace awareness and switching much easier.  I
downloaded the kubectx.deb package from the unstable Debian package repository.

        $ sudo apt install ~/Downloads/kubectx_0.6.3-2_all.deb

1. [set up access to my new GCP cluster through
`kubectl`](https://cloud.google.com/kubernetes-engine/docs/how-to/cluster-access-for-kubectl).

        $ gcloud container clusters get-credentials test

    Incidentally, I learned about
    [tput](https://linux.101hacks.com/ps1-examples/prompt-color-using-tput/), a
    much nicer way to set color and text attributes than raw ANSI codes. A [full
    list of color codes can be
    generated](https://unix.stackexchange.com/questions/269077/tput-setaf-color-table-how-to-determine-color-codes).

1. Get Cassandra "installed" in the cluster. Reread original
[Cassandra-on-Minkube
instructions](https://kubernetes.io/docs/tutorials/stateful-application/cassandra/).

    Separated the `StorageClass` section of cassandra-statefulset.yaml into a
    different file and changed the name so that the storage class names would match
    in the Minikube and GCP environments. Notice that the name _standard_ matches
    the default GCP K8s storage class name (it was _fast_ before).

        kind: StorageClass
        apiVersion: storage.k8s.io/v1
        metadata:
          name: standard
        provisioner: k8s.io/minikube-hostpath
        parameters:
          type: pd-ssd

    Then

        $ k apply -f cassandra-statefulset.yaml     

    It took almost 4 minutes to spin up the three-node Cassandra cluster.     

1. Upload the Spring Boot Web Service Docker Image.

        $ gcloud auth configure-docker
        The following settings will be added to your Docker config file 
        located at [/home/ed/.docker/config.json]:
        {
        "credHelpers": {
            "gcr.io": "gcloud", 
            "us.gcr.io": "gcloud", 
            "eu.gcr.io": "gcloud", 
            "asia.gcr.io": "gcloud", 
            "staging-k8s.gcr.io": "gcloud", 
            "marketplace.gcr.io": "gcloud"
        }
        }

        Do you want to continue (Y/n)?  y

        Docker configuration file updated.

        $ docker tag service-that-logs:latest gcr.io/maximal-copilot-249415/service-that-logs

        $ docker push gcr.io/maximal-copilot-249415/service-that-logs
        The push refers to repository [gcr.io/maximal-copilot-249415/service-that-logs]
        399738b74712: Pushed 
        2ad1448ed264: Pushed 
        597ef0c21b96: Pushed 
        1bfeebd65323: Layer already exists 
        latest: digest: sha256:56ecf745d2eea68dcbd01c6dac8387355aa19434afef3da1eefc0635c960ad51 size: 1163

        $ gcloud container images list
        NAME
        gcr.io/maximal-copilot-249415/service-that-logs
        Only listing images in gcr.io/maximal-copilot-249415. Use --repository to list images in other repositories.

1. Start the Spring Boot Web Service.

        $ k apply -f service-that-logs.yaml

        $ k get service service-that-logs
        NAME                TYPE           CLUSTER-IP    EXTERNAL-IP      PORT(S)          AGE
        service-that-logs   LoadBalancer   10.0.14.167   104.154.232.75   8081:30434/TCP   49m

1. Test the Spring Boot Web Service.

        $ curl 104.154.232.75:8081 && echo && date -u
        Howdy, curl/7.58.0!
        Sat Aug 10 18:29:15 UTC 2019

        $ k logs service-that-logs-5fd948454c-wmvwx | grep curl
        2019-08-10 18:29:15.050  INFO 1 --- [nio-8080-exec-9] com.tzahk.Controller : request from curl/7.58.0

        $ # prove that the web app can connect to the Cassandra cluster
        $ curl http://104.154.232.75:8081/v && echo
        3.11.2

1. Tear down the GCP K8s cluster.

        $ gcloud container clusters delete test
        The following clusters will be deleted.
        - [test] in [us-central1-a]

        Do you want to continue (Y/n)?  y

        Deleting cluster test...done.                                                                                                                                                           
        Deleted [https://container.googleapis.com/v1/projects/maximal-copilot-249415/zones/us-central1-a/clusters/test].    

1. Finally, check in the GCP Control Panel to make sure that your cluster and
storage is deleted. *Manually delete your Docker images.*

Saturday, August 17, 2019
==========================

Repeat GCP Procedure
---------------------

1. Fire up Minikube and confirm the application still works.

    ```bash
    $ sudo minikube start
    [sudo] password for ed: 
    ...
    🏄  Done! kubectl is now configured to use "minikube"

    $ k get svc service-that-logs -o wide
    NAME                TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE   SELECTOR
    service-that-logs   LoadBalancer   10.104.121.54   <pending>     8081:30434/TCP   11d   run=service-that-logs
    
    $ curl 10.104.121.54:8081 && echo
    Howdy, curl/7.58.0!
    
    $ curl 10.104.121.54:8081/v && echo
    3.11.2
    ```

1. Recreate the cluster in GCP using the command generated by the UI last week:

    ```bash
    $ gcloud beta container \
        --project "maximal-copilot-249415" clusters create "test" \
        --zone "us-central1-a" \
        --no-enable-basic-auth \
        --cluster-version "1.12.8-gke.10" \
        --machine-type "n1-standard-1" \
        --image-type "COS" \
        --disk-type "pd-standard" \
        --disk-size "100" \
        --scopes "https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write","https://www.googleapis.com/auth/monitoring","https://www.googleapis.com/auth/servicecontrol","https://www.googleapis.com/auth/service.management.readonly","https://www.googleapis.com/auth/trace.append" \
        --num-nodes "3" \
        --enable-cloud-logging \
        --enable-cloud-monitoring \
        --enable-ip-alias \
        --network "projects/maximal-copilot-249415/global/networks/default" \
        --subnetwork "projects/maximal-copilot-249415/regions/us-central1/subnetworks/default" \
        --default-max-pods-per-node "110" \
        --addons HorizontalPodAutoscaling,HttpLoadBalancing \
        --enable-autoupgrade \
        --enable-autorepair

    Creating cluster test in us-central1-a... Cluster is being health-checked (master is healthy)...done. 
    Created [https://container.googleapis.com/v1beta1/projects/maximal-copilot-249415/zones/us-central1-a/clusters/test].
    To inspect the contents of your cluster, go to: https://console.cloud.google.com/kubernetes/workload_/gcloud/us-central1-a/test?project=maximal-copilot-249415
    kubeconfig entry generated for test.
    NAME  LOCATION       MASTER_VERSION  MASTER_IP       MACHINE_TYPE   NODE_VERSION   NUM_NODES  STATUS
    test  us-central1-a  1.12.8-gke.10   35.222.139.171  n1-standard-1  1.12.8-gke.10  3          RUNNING
    ```

1. [set up access to GCP cluster through
`kubectl`](https://cloud.google.com/kubernetes-engine/docs/how-to/cluster-access-for-kubectl),
then clean up the configuration for the old cluster that remained in
`~/.kube/config` using `kubectx`.

    ```bash
    $ gcloud container clusters get-credentials test

    $ kc
    gke_maximal-copilot-249415_us-central1-a_test
    minikube
    test
    
    $ kc -d test
    Deleting context "test"...
    deleted context test from /home/ed/.kube/config

    $ kc
    gke_maximal-copilot-249415_us-central1-a_test
    minikube
    
    $ kc test=gke_maximal-copilot-249415_us-central1-a_test
    Context "gke_maximal-copilot-249415_us-central1-a_test" renamed to "test".
    
    $ kc
    minikube
    test
    ```

1. Deploy Cassandra.

        $ k apply -f cassandra-service.yaml
        $ k apply -f cassandra-statefulset.yaml     

    **NOTE**: It seems important to start the Service *before* you start the
    StatefulSet. When I started the StatefulSet first, I saw the following
    error in the second Cassandra node's logs:

        WARN  14:22:31 Seed provider couldn't lookup host cassandra-0.cassandra.default.svc.cluster.local
        Exception (org.apache.cassandra.exceptions.ConfigurationException) encountered during startup: The seed provider lists no seeds.
        The seed provider lists no seeds.
        ERROR 14:22:31 Exception encountered during startup: The seed provider lists no seeds.

1. Upload the Spring Boot Web Service Docker Image.

        $ docker push gcr.io/maximal-copilot-249415/service-that-logs
        The push refers to repository [gcr.io/maximal-copilot-249415/service-that-logs]
        ...
        latest: digest: sha256:56ecf745d2eea68dcbd01c6dac8387355aa19434afef3da1eefc0635c960ad51 size: 1163


        $ gcloud container images list
        NAME
        gcr.io/maximal-copilot-249415/service-that-logs
        Only listing images in gcr.io/maximal-copilot-249415. Use --repository to list images in other repositories.

1. Start the Spring Boot Web Service and test it.

        $ k apply -f service-that-logs.yaml

        $ k get svc service-that-logs
        NAME                TYPE           CLUSTER-IP   EXTERNAL-IP      PORT(S)          AGE
        service-that-logs   LoadBalancer   10.0.4.99    104.197.69.218   8081:30434/TCP   88s

        $ curl 104.197.69.218:8081 && echo && date -u
        Howdy, curl/7.58.0!
        Sat Aug 10 18:29:15 UTC 2019

        $ k logs $(k get pod -l=run=service-that-logs -o name) | grep curl
2019-08-17 14:43:20.420  INFO 1 --- [nio-8080-exec-1] com.tzahk.Controller                     : request from curl/7.58.0

        $ curl 104.197.69.218:8081/v && echo
        3.11.2


Debugging an Application Running in Minikube
---------------------------------------------

First, confirmed that I can start the Spring Boot Web App within VS Code and
debug it.  This required that I forward a port into the Minikube Cassandra pod.

    $ k port-forward $(k get pods -o name -l=app=cassandra) 9042

Trying to meld together these two articles:

- [Four key steps to configure a remote debugging session for Java Spring Boot microservices running in Docker containers](https://www.ibm.com/cloud/blog/four-steps-to-debugging-java-spring-boot-microservices-running-in-docker-containers)

- [Running and Debugging Java in VS Code](https://code.visualstudio.com/docs/java/java-debugging)

Here are the Four Steps (mentioned in the article):

1. Build the service in debug mode.

    added to `pom.xml` (from https://stackoverflow.com/a/16480700/150)

    ```xml
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.8.1</version>
        <configuration>
            <source>8</source>
            <target>8</target>
            <debug>true</debug>
            <debuglevel>lines,vars,source</debuglevel> 
        </configuration>
    </plugin>
    ```

    Then rebuilt and re-packaged:

        $ mvn package

1. Modify the Dockerfile to expose the debug port on the Docker image, and start/run the application in debug mode...

    ```docker
    FROM adoptopenjdk/openjdk11:alpine-jre
    COPY target/service-that-logs-0.1.0.jar .
    EXPOSE 8080/tcp
    EXPOSE 8000/tcp
    ENTRYPOINT ["java","-jar","-Xdebug","-Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=n","service-that-logs-0.1.0.jar"]
    ```

    ...and rebuild the Docker image.

        $ docker build --tag=service-that-logs:debug -f Dockerfile.debug .

1. Deploy the Docker container with the debugger port exposed.

    Modify the Deployment YAML to open the debugger port and refer to the
    "debug" container image...

    ```yaml
      - envFrom:
        - configMapRef:
            name: service-that-logs-config
        image: service-that-logs:debug
        imagePullPolicy: Never
        name: service-that-logs
        ports:
        - containerPort: 8080
          protocol: TCP
        - containerPort: 8000
          protocol: TCP  
    ```

    ...and re-deploy it.

        $ k delete deploy service-that-logs
        deployment.extensions "service-that-logs" deleted

        $ k apply -f ../service-that-logs.minikube.yaml

1. Run a remote debugging session from VS Code.

    Add an "attach" debugger configuration.

    ```json
    {
        "type": "java",
        "name": "Minikube - Application",
        "request": "attach",
        "hostName": "localhost",
        "port": 8000
    },
    ```

    Forward the debugger port into the Pod.

        $ k port-forward $(k get pod -o name -l run=service-that-logs) 8000

It works!

This is handy for determining if you're running the images you think you're
running: [List All Running Container
Images](https://kubernetes.io/docs/tasks/access-application-cluster/list-all-running-container-images/).

I also saw a suggestion that I could enable debugging using an environment
variable (`JAVA_TOOL_OPTIONS`) rather than a command line switch, but I don't
see how to expose/hide the port dynamically. (It doesn't seem wise to leave it
open even in a "release" build.)

-e "JAVA_TOOL_OPTIONS=\"-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n\""


Debugging an Application Running in GCP K8s
--------------------------------------------

1. Replace the tagged local Docker image with a debug-enabled image.

        $ docker rmi gcr.io/maximal-copilot-249415/service-that-logs:latest

        $ docker tag service-that-logs:debug gcr.io/maximal-copilot-249415/service-that-logs:debug

1. Push the Docker image to the GCP repository.

        $ docker push gcr.io/maximal-copilot-249415/service-that-logs

1. Modify the deployment specification to reference the debug-enabeld Docker
image and expose the remote debugger port.

    ```bash
    $ git diff service-that-logs.gcp.yaml 
    diff --git a/service-that-logs.gcp.yaml b/service-that-logs.gcp.yaml
    index fe8fda0..89e590e 100644
    --- a/service-that-logs.gcp.yaml
    +++ b/service-that-logs.gcp.yaml
    @@ -46,12 +46,14 @@ spec:
        - envFrom:
            - configMapRef:
                name: service-that-logs-config
    -        image: gcr.io/maximal-copilot-249415/service-that-logs
    +        image: gcr.io/maximal-copilot-249415/service-that-logs:debug
            imagePullPolicy: IfNotPresent
            name: service-that-logs
            ports:
            - containerPort: 8080
            protocol: TCP
    +        - containerPort: 8000
    +          protocol: TCP
            resources: {}
            terminationMessagePath: /dev/termination-log
            terminationMessagePolicy: File
    ```


Cleanup
--------

1. Delete the GCP K8s cluster.

        $ gcloud container clusters delete test
        ...                                                                                    
        Deleted [https://container.googleapis.com/v1/projects/maximal-copilot-249415/zones/us-central1-a/clusters/test].

1. Delete the Container image. I did this from the command line using `gcloud
container images list` and `gcloud container images delete <image-name>` but it
was a little cumbersome. I should refine this process next time around.

1. Stop Minikube.

1. Confirm in the GCP Control Panel that your cluster, storage, and images are
deleted.


Saturday, August 24, 2019
==========================

Repeat GCP Procedure
---------------------

1. Fire up Minikube and confirm the application still works.

    ```bash
    $ sudo minikube start
    [sudo] password for ed: 
    ...
    🏄  Done! kubectl is now configured to use "minikube"

    $ ./test_service_that_logs.sh 
    Testing context minikube 10.104.121.54:8081...
      / returned Howdy, curl/7.58.0!
      /v returned 3.11.2

    ```

1. Recreate the cluster in GCP, configure `kubectl` access, and deploy the
whole system.

    ```bash
    $ ./create_gcp_cluster.sh
    ...
    NAME  LOCATION       MASTER_VERSION  MASTER_IP      MACHINE_TYPE   NODE_VERSION   NUM_NODES  STATUS
    test  us-central1-a  1.12.8-gke.10   104.154.99.74  n1-standard-1  1.12.8-gke.10  3          RUNNING

    $ gcloud container clusters get-credentials test
    Fetching cluster endpoint and auth data.
    kubeconfig entry generated for test.

    $ kc -d test
    Deleting context "test"...
    deleted context test from /home/ed/.kube/config

    $ kc test=gke_maximal-copilot-249415_us-central1-a_test
    Context "gke_maximal-copilot-249415_us-central1-a_test" renamed to "test".

    $ k apply -f cassandra-service.yaml

    $ k apply -f cassandra-statefulset.yaml     

    $ docker push gcr.io/maximal-copilot-249415/service-that-logs
    The push refers to repository [gcr.io/maximal-copilot-249415/service-that-logs]
    ...
    latest: digest: sha256:56ecf745d2eea68dcbd01c6dac8387355aa19434afef3da1eefc0635c960ad51 size: 1163

    $ k apply -f service-that-logs.gcp.yaml

    $ ./test_service_that_logs.sh 
    Testing context test 35.225.175.156:8081...
      / returned Howdy, curl/7.58.0!
      /v returned 3.11.2
    ```

Consolidate Application Configuration
--------------------------------------

1. Move the Cassandra data center value (and the rest of the
_service-that-logs_) configuration into a separate file
`common.configmap.yaml`.

1. Apply the changes.
    - on Minikube, Cassandra StatefulSet must be deleted and re-created
    - on GCP, rolling update works as expected
    - _service-that-logs_ `apply` works in both environments
    - run the test script in both environments

1. Write a Python script to produce text templates from a YAML template and
YAML parameters. Combine all of the resource description YAML files into a
single file and factor out the Minikube- and GCP-specific values into a JSON
file.

    Now we can apply the resource descriptions all at once, plugging in
    variables.

        $ k apply -f <(./rendertemplate.py app.yaml.template app.gcp.json)

Cleanup
--------

1. Delete the GCP K8s cluster and Google Container Registry image.

        $ gcloud container clusters delete test

        $ export IMAGE_NAME=$(gcloud container images list --format=config | sed 's/name = //')
        
        $ export IMAGE_TAG=$(gcloud container images list-tags gcr.io/maximal-copilot-249415/service-that-logs --format=config | grep digest | sed 's/digest = sha256://')

        $ 

1. Delete the Container image. I did this from GCP Web Console because no
matter what I did, `gcloud` gave me useless, frustrating error messages about
the format of my image name/tag.

        ERROR: (gcloud.container.images.delete) [gcr.io/maximal-copilot-123456/service-that-logs@sha265:68ba913777862fbd07d27d1284293c4e8a0d0a188b7a98efe8d5876fe3123456] digest must be of the form "sha256:<digest>".

1. Stop Minikube.

1. Confirm in the GCP Control Panel that your cluster, storage, and images are
deleted.


Next Steps
===========

1. How would Terraform and/or Helm improve upon a text template solution and
`kubectl apply`?
    
1. Implement a feature-toggle that doesn't require pod recreation.

1. Add Kafka to the application.

1. Add Flink to the application.
