# DreamPort 独立后端镜像
# 构建: docker build -t dreamport .
# 运行: docker run -d -p 18898:18898 -p 18899:18899 \
#   -v /opt/dreamport/config.yml:/app/config.yml \
#   -v dreamport-docs:/app/docs -v dreamport-uploads:/app/static/uploads dreamport
# （首次启动自动生成 /app/config.yml，编辑后 docker restart）
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY dreamport-server/target/dreamport-server-*.jar app.jar

# 运行时数据目录（docs/ 文档、static/uploads/ 上传、email/ 模板覆盖）
RUN mkdir -p /app/docs /app/static/uploads /app/email
VOLUME ["/app/docs", "/app/static/uploads", "/app/email"]

EXPOSE 18898 18899
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
