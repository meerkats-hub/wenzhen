import {defineConfig, loadEnv} from 'vite'
import vue from '@vitejs/plugin-vue'
import autoprefixer from 'autoprefixer'
import {createHtmlPlugin} from 'vite-plugin-html'
import path from 'path'

const config = ({mode}) => {
    const isProd = (mode === 'pro' || mode === 'loc')
    const envPrefix = 'APP_'
    const {APP_TITLE = ''} = loadEnv(mode, process.cwd(), envPrefix)
    const env = loadEnv(mode, process.cwd(), '')
    // console.log('env = ', env);
    // console.log('mode = ', mode);
    return {
        base: './',
        sourceMap: false,
        plugins: [
            vue(),
            createHtmlPlugin({
                minify: isProd,
                inject: {
                    data: {
                        title: APP_TITLE,
                    },
                }
            })
        ],
        build: {
            target: 'es2015',
            outDir: path.resolve(__dirname, 'dist'),
            assetsDir: 'assets',
            assetsInlineLimit: 8192,
            sourcemap: !isProd, // 配置是否生成用于开发调试的 .map文件
            emptyOutDir: true,
            rollupOptions: {
                input: path.resolve(__dirname, 'index.html'),
                output: {
                    chunkFileNames: 'js/[name].[hash].js',
                    entryFileNames: 'js/[name].[hash].js',
                    // assetFileNames: "assets/[name].[hash].[ext]",
                }
            }
        },
        envPrefix,
        resolve: {
            alias: [
                {find: /^@\//, replacement: `${path.resolve(__dirname, 'src')}/`},
                {find: /^~/, replacement: ''}
            ],
            extensions: ['.js', '.mjs', '.vue', '.json', '.less', '.css']
        },
        css: {
            postcss: {
                plugins: [
                    autoprefixer
                ],
            },
            preprocessorOptions: {
                less: {
                    javascriptEnabled: true,
                    additionalData: `@import "${path.resolve(__dirname, 'src/styles/variable.less')}";`
                }
            }
        },
        server: {
            open: true,
            host: '0.0.0.0',
            proxy: {
                '/api': {
                    target: 'http://localhost:8080',
                    changeOrigin: true
                },
                '/webatch': {
                    target: env.APP_BASE_API_URL,
                    changeOrigin: true
                },
                '/we': {
                    target: env.APP_TASK_API_URL,
                    changeOrigin: true
                }
            }
        },
        preview: {
            port: 5000
        },
        vueCompilerOptions: {
            isCustomElement: tag => tag === 'We'
        }
    }
}

export default defineConfig(config)
