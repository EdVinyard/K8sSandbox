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
lean 141 MB, compared to the ~250 MB `openjdk/11-jre` image and the portly
600+ MB `openjdk/11`.

Other examples seem really interested in `COPY`ing their build artifacts in
other directories (e.g., `/app/lib`, or `/usr/src`); I'm not sure why. Just
putting it in the working directory seems to work OK.

Then, with the help of 
- https://docs.docker.com/get-started/part2/#build-the-app
- https://docs.docker.com/engine/reference/commandline/run/#publish-or-expose-port

I ran

    $ docker build --tag=service-that-logs:0.1.0 .
    
    $ docker run -p 127.0.0.1:80:8080/tcp service-that-logs:0.1.0

Next Steps
===========

1. Test the Cassandra client app connecting to a Cassandra server running in
Minikube (via an exposed K8s Service, I think).

1. Rework this project to use Maven instead of Gradle.
    - https://codefresh.io/howtos/using-docker-maven-maven-docker/
    - https://ro14nd.de/dmp-not-so-bad

1. Deploy both the Java Spring application and Cassandra into **Minikube**.

1. Deploy both the Java Spring application and Cassandra into **GCP**.

1. Debug the Cassandra client application running in Docker/K8s.
