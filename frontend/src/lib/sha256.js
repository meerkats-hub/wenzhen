const sha256 = function (input) {
    const encoder = new TextEncoder();
    let data = encoder.encode(input);

    // 消息填充
    let lenBits = data.length * 8;
    let k = (448 - ((lenBits + 1) % 512)) % 512;
    if (k < 0) k += 512;

    const padded = new Uint8Array(data.length + 1 + k + 8);
    padded.set(data);
    padded[data.length] = 0x80; // 添加1字节的0x80

    // 添加原始长度（小端序）
    const lenBytes = new Uint8Array(8);
    for (let i = 0; i < 8; i++) {
        lenBytes[i] = (lenBits >>> (i * 8)) & 0xff;
    }
    padded.set(lenBytes, data.length + 1 + k);

    // 初始化哈希值
    let H = [
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    ];

    // 生成常量表K
    const K = Array.from({length: 64}, (_, t) =>
        Math.floor(0x100000000 * Math.abs(Math.sin(t + 1)))
    );

    // 处理每个512位块
    for (let i = 0; i < padded.length; i += 64) {
        const chunk = padded.subarray(i, i + 64);
        let W = new Uint32Array(64);

        // 拆分16个32位字（大端序）
        for (let t = 0; t < 16; t++) {
            W[t] = (chunk[t * 4] << 24) | (chunk[t * 4 + 1] << 16) |
                (chunk[t * 4 + 2] << 8) | chunk[t * 4 + 3];
        }

        // 扩展到64个字
        for (let t = 16; t < 64; t++) {
            const s0 = (W[t - 15] >>> 7) ^ (W[t - 15] >>> 18) ^ (W[t - 15] >> 3);
            const s1 = (W[t - 2] >>> 17) ^ (W[t - 2] >>> 19) ^ (W[t - 2] >> 10);
            W[t] = (W[t - 16] + s0 + W[t - 7] + s1) | 0;
        }

        // 压缩函数
        let [a, b, c, d, e, f, g, h] = H;

        for (let t = 0; t < 64; t++) {
            const S1 = (e >>> 6) ^ (e >>> 11) ^ (e >>> 25);
            const ch = (e & f) ^ (~e & g);
            const temp1 = (h + S1 + ch + K[t] + W[t]) | 0;

            const S0 = (a >>> 2) ^ (a >>> 13) ^ (a >>> 10);
            const maj = (a & b) ^ (a & c) ^ (b & c);
            const temp2 = (S0 + maj) | 0;

            h = g;
            g = f;
            f = e;
            e = (d + temp1) | 0;
            d = c;
            c = b;
            b = a;
            a = (temp1 + temp2) | 0;
        }

        // 更新哈希值
        H = H.map((val, idx) => (val + [a, b, c, d, e, f, g, h][idx]) | 0);
    }

    // 转换为十六进制字符串（无符号）
    return H.map(x =>
        x.toString(16).padStart(8, '0')
    ).join('');
}

export default sha256