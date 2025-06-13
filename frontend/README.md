# typha-h5

Typha H5是在线自助式进行反向代理配置的 http server h5前端管理台

## 安装

```shell
npm install
```

## 运行

```shell
npm run serve
```

## 构建

### Build for Production

```shell
npm run build
```

### Build for Staging

```shell
npm run build:staging
```

## Preview

### Preview for Production

```shell
npm run preview
```

### Preview for Staging

```shell
npm run preview:staging
```

## Run Eslint

### Run without Fixing

- This operation will also run before git-commit.

```shell
npm run lint
```

### Run with Fixing

```shell
npm run lint:fix
```

## .env Description

- This project exposes environment variables on `import.meta.env` object.
- Different modes (development/staging/production) correspond to different environment files (.env.*).
- .env file is always included, duplicate variables are overwritten by the specific mode file (.env.*).
