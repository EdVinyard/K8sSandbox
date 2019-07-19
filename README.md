Saturday, July 13, 2019

Get the Spring "Hello, World!" container running in Minikube.

    $ # build and create a Docker image
    $ gradle dockerClean
    $ gradle docker

    $ # create a local Kubernetes instance
    $ sudo minikube start --vm-driver=none
    $ sudo minikube dashboard$ sudo minikube dashboard
    [sudo] password for ed:
    🤔  Verifying dashboard health ...
    ... blah, blah, blah ...
    xdg-open: no method available for opening 'http://127.0.0.1:33689/api/v1/namespaces/kube-system/services/http:kubernetes-dashboard:/proxy/'
    $ # open this URL in your web browser manually --^

    $ # deploy a container image - create a Deployment
    $ sudo kubectl run hello-spring --image=tzahk/spring-hello-world --image-pull-policy=Never --port=8080
    $ sudo kubectl get deployment
    NAME           READY   UP-TO-DATE   AVAILABLE   AGE
    hello-spring   1/1     1            1           6m22s

    $ # expose a service - create a Service
    $ sudo kubectl expose deployment hello-spring --type=LoadBalancer --port=8081 --target-port=8080
    $ sudo kubectl get service -o wide
    NAME           TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE     SELECTOR
    hello-spring   LoadBalancer   10.105.65.170   <pending>     8081:31699/TCP   3m22s   run=hello-spring
    kubernetes     ClusterIP      10.96.0.1       <none>        443/TCP          88m     <none>

    $ firefox http://10.105.65.170:8081
