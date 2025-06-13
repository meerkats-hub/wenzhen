import {createRouter, createWebHashHistory} from 'vue-router'

const routes = [
    {
        path: '/',
        name: 'home',
        component: () => import('@/views/Report.vue')
    }
]

const router = createRouter({
    routes,
    preloadAll: true,
    history: createWebHashHistory(),
    scrollBehavior() {
        return {top: 0}
    }
})

export default router
