FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/datapulse-frontend/browser /usr/share/nginx/html/browser

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
