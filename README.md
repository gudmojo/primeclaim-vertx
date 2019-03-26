#primeclaim-vertx

A toy project to play around with Vert.x

It connects to a postgres database:

docker run --name some-postgres -p 5432:5432 -e POSTGRES_PASSWORD=mysecretpassword postgres

It exposes a http endpoint:

GET /

GET /ping
