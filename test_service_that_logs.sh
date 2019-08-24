#!/bin/bash

export CONTEXT=$(kubectl config current-context)
export PORT=$(kubectl get svc service-that-logs -o jsonpath="{.spec.ports[0].port}")

if [ "$CONTEXT" == "minikube" ]; then
    export HOST=$(kubectl get svc service-that-logs -o jsonpath="{.spec.clusterIP}")
else
    export HOST=$(kubectl get svc service-that-logs -o jsonpath="{.status.loadBalancer.ingress[0].ip}")
fi

echo Testing context $CONTEXT $HOST:$PORT...
echo \ \ /  returned $(curl -s "$HOST:$PORT/")
echo \ \ /v returned $(curl -s "$HOST:$PORT/v")
