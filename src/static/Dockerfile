FROM node:14.16.0
LABEL message="static"
COPY finger-memory-web/ /app/
WORKDIR /app/
RUN npm install --registry=https://registry.npm.taobao.org \
    && npm run build \
    && npm install -g http-server --registry=https://registry.npm.taobao.org
ENTRYPOINT http-server ./dist/ -p 5500
EXPOSE 5500