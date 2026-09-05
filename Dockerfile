# DreamPort 独立后端镜像
# 构建: docker build -t dreamport-server .
# 运行: docker run -p 18898:18898 -p 18899:18899 \
#   -e WL_DB_HOST=... -e WL_DB_NAME=... -e WL_DB_USER=... -e WL_DB_PASSWORD=... \
#   -e WL_JWT_SECRET=... dreamport-server
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY dreamport-server/target/dreamport-server-*.jar app.jar

# 运行时数据目录（docs/ 文档、static/uploads/ 上传、email/ 模板覆盖）
RUN mkdir -p /app/docs /app/static/uploads /app/email
VOLUME ["/app/docs", "/app/static/uploads", "/app/email"]

EXPOSE 18898 18899
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# 生产: 追加 --spring.profiles.active=mysql
