export IMAGE=service-that-logs
export DEPLOYMENT=service-that-logs
export SERVICE=service-that-logs
export LOG_LEVEL=DEBUG
export APPLICATION_PORT=8080
export SERVICE_PORT=8081

# from https://stackoverflow.com/a/25515370/150
yell() { echo "$0: $*" >&2; }
die() { yell "$*"; exit 111; }
try() { "$@" || die "FAILED: $*"; }

if kubectl get service $SERVICE; then
    try kubectl delete service $SERVICE
fi

if kubectl get deployment $DEPLOYMENT; then
    try kubectl delete deployment $DEPLOYMENT
fi

set IMAGE_ID=$(docker images $IMAGE --format "{{.ID}}")
if test "$IMAGE_ID"; then 
    try docker rmi $IMAGE_ID
fi

try mvn package
try docker build --tag=$IMAGE .
try kubectl run $DEPLOYMENT \
    --image=$IMAGE \
    --image-pull-policy=Never \
    --port=$APPLICATION_PORT \
    --env="LOG_LEVEL=$LOG_LEVEL"
try kubectl expose deployment $SERVICE \
    --type=LoadBalancer \
    --port=$SERVICE_PORT \
    --target-port=$APPLICATION_PORT

export NODE_PORT=$( kubectl get services service-that-logs \
    -o=jsonpath="{.spec.ports[0].nodePort}" )
export URL=http://localhost:$NODE_PORT/

echo Application is exposed at $URL
