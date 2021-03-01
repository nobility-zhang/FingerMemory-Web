FROM node:14.16.0
LABEL message="static-server"
COPY finger-memory-web/ /app/
WORKDIR /app/
RUN npm install --registry=https://registry.npm.taobao.org
ENTRYPOINT npm run start
EXPOSE 3000