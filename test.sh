
# Finally successful testing with sam cli
mvn package
docker build -f Dockerfile.lambda -t repos-indexing:1 .

export DOCKER_HOST=unix:///Users/takesson/.docker/run/docker.sock
sam local start-lambda --debug

# Second terminal
awslocal lambda invoke --function-name HelloLambdaFunction --endpoint-url=http://127.0.0.1:3001 --cli-binary-format raw-in-base64-out --payload '{"Records":[]}' output.txt
