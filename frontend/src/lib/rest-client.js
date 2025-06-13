import axios from 'axios'

const restWithAuth = axios.create({
    baseURL: import.meta.env.APP_BASE_API_URL,
    timeout: 60000,
    headers: {
        'Provider': 'Wenear community'
    }
})

// 请求拦截
restWithAuth.interceptors.request.use(config => {
    config.headers['Access-Control-Allow-Origin'] = 'http://localhost:3000'
    config.headers['Authorization'] = sessionStorage.getItem("Authorization")
    // config.headers['Addr'] = window.location.href
    return config
})

// 响应拦截
restWithAuth.interceptors.response.use(res => {
    return res
}, error => {
    if (error.response === undefined) {
        sessionStorage.setItem('loginExpireMessage', '连接后端服务失败')
    } else {
        if (error.response.status === 401) {
            sessionStorage.removeItem('Authorization')
            sessionStorage.setItem('loginExpireMessage', '登录过期了, 请重新登录')
            if (!window.location.href.endsWith('/login')) {
                window.location = '/'
            }
        }
        if (error.response.status === 400) {
            if (error.response.data !== undefined && error.response.data.msg !== undefined
                && error.response.data.msg != null) {
                error.message = '' + error.response.data.msg
            } else {
                error.message = 'Bad request.'
            }
        }
    }
    return Promise.reject(error)
})


export default restWithAuth