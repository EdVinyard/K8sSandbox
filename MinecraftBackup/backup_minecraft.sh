#!/bin/bash

# Use the Google Cloud IAM Service Account credentials store in a JSON file,
# (a volume-mounted secret). This is what gsutil relies on, not the environment
# var GOOGLE_APPLICATION_CREDENTIALS.  See https://serverfault.com/a/901950/7209
gcloud auth activate-service-account --key-file=/var/secrets/google/key.json

datetime=$(date '+%Y-%m-%dT%H%M') # Example: 2019-10-07T1534

# Both the Pod name and the Deployment name change when deploying with Helm,
# because the Helm Release is used as a prefix. E.g., given the release name
# "mc6", the Deployment name will be "mc6-minecraft".  Using grep here means
# that even when the Deployment and Pod name prefixes change, we'll still 
# identify the correct Pod.
pod=$(kubectl get pod -n minecraft -o name | grep '\-minecraft\-' | sed 's:pod/::')
# Example: pod=mc6-minecraft-6c69dcc8b5-ngjkw

backup_filename=${pod}.${datetime}.tgz 
# Example: mc6-minecraft-6c69dcc8b5-ngjkw.2019-10-07T1534.tgz

# Archive and compress the minecraft world folder.
kubectl exec $pod -- tar czf - world > ~/$backup_filename

gsutil cp ~/$backup_filename gs://tzahk-minecraft-backup/
