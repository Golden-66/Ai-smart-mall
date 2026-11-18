# 多阶段构建: 同一个 Dockerfile 通过 MODULE 参数构建任意微服务
#   docker build --build-arg MODULE=gateway -t mall-gateway .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
ARG MODULE
RUN mvn -q -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-1.0.0.jar /app/app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
