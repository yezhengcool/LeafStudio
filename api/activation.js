/**
 * Vercel Serverless Function for Activation API
 * 部署到 Vercel 后提供 HTTPS API 接口
 */

import { neon } from '@neondatabase/serverless';

export const config = {
    runtime: 'edge',
};

const sql = neon(process.env.DATABASE_URL);

export default async function handler(req) {
    // 只允许 POST 请求
    if (req.method !== 'POST') {
        return new Response(JSON.stringify({ error: 'Method not allowed' }), {
            status: 405,
            headers: { 'Content-Type': 'application/json' },
        });
    }

    try {
        const body = await req.json();
        const { action, machineCode, activationCode } = body;

        if (!action || !machineCode) {
            return new Response(JSON.stringify({ error: 'Missing required parameters' }), {
                status: 400,
                headers: { 'Content-Type': 'application/json' },
            });
        }

        // 检查激活状态
        if (action === 'check') {
            // 获取服务器时间
            const timeResult = await sql`SELECT EXTRACT(EPOCH FROM NOW()) * 1000 AS server_time`;
            const serverTime = parseInt(timeResult[0].server_time);

            // 查询设备记录
            const deviceResult = await sql`
        SELECT expiry_time FROM activation_records WHERE machine_code = ${machineCode}
      `;

            if (deviceResult.length === 0) {
                // 设备不存在，自动注册（3天试用）
                const trialDays = 3;
                const expiryTime = serverTime + (trialDays * 24 * 60 * 60 * 1000);

                await sql`
          INSERT INTO activation_records (machine_code, activation_code, activation_time, expiry_time, device_note)
          VALUES (${machineCode}, 'TRIAL-AUTO', ${serverTime}, ${expiryTime}, '自动注册试用')
        `;

                return new Response(JSON.stringify({
                    success: true,
                    isValid: true,
                    remainingSeconds: (expiryTime - serverTime) / 1000,
                    expiryTime: expiryTime,
                    message: `试用期剩余 ${trialDays} 天`
                }), {
                    status: 200,
                    headers: { 'Content-Type': 'application/json' },
                });
            }

            // 设备已存在
            const expiryTime = parseInt(deviceResult[0].expiry_time);
            const remainingMillis = expiryTime - serverTime;
            const isValid = remainingMillis > 0;

            return new Response(JSON.stringify({
                success: true,
                isValid: isValid,
                remainingSeconds: isValid ? remainingMillis / 1000 : 0,
                expiryTime: expiryTime,
                message: isValid ? '已激活' : '激活已过期'
            }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            });
        }

        // 激活码激活
        if (action === 'activate') {
            if (!activationCode) {
                return new Response(JSON.stringify({ error: 'Missing activation code' }), {
                    status: 400,
                    headers: { 'Content-Type': 'application/json' },
                });
            }

            // 验证激活码
            const codeResult = await sql`
        SELECT is_used, duration_days FROM activation_codes WHERE code = ${activationCode}
      `;

            if (codeResult.length === 0) {
                return new Response(JSON.stringify({
                    success: false,
                    message: '激活码无效'
                }), {
                    status: 200,
                    headers: { 'Content-Type': 'application/json' },
                });
            }

            const isUsed = codeResult[0].is_used;
            const durationDays = codeResult[0].duration_days;

            if (isUsed) {
                return new Response(JSON.stringify({
                    success: false,
                    message: '激活码已被使用'
                }), {
                    status: 200,
                    headers: { 'Content-Type': 'application/json' },
                });
            }

            // 获取服务器时间
            const timeResult = await sql`SELECT EXTRACT(EPOCH FROM NOW()) * 1000 AS server_time`;
            const serverTime = parseInt(timeResult[0].server_time);

            // 查询设备是否存在
            const deviceResult = await sql`
        SELECT expiry_time FROM activation_records WHERE machine_code = ${machineCode}
      `;

            const durationMillis = durationDays * 24 * 60 * 60 * 1000;
            let newExpiryTime;

            if (deviceResult.length > 0) {
                // 续费
                const currentExpiry = parseInt(deviceResult[0].expiry_time);
                newExpiryTime = (currentExpiry > serverTime ? currentExpiry : serverTime) + durationMillis;

                await sql`
          UPDATE activation_records 
          SET expiry_time = ${newExpiryTime}, activation_code = ${activationCode}, updated_at = NOW()
          WHERE machine_code = ${machineCode}
        `;
            } else {
                // 新激活
                newExpiryTime = serverTime + durationMillis;
                await sql`
          INSERT INTO activation_records (machine_code, activation_code, activation_time, expiry_time)
          VALUES (${machineCode}, ${activationCode}, ${serverTime}, ${newExpiryTime})
        `;
            }

            // 标记激活码已使用
            await sql`
        UPDATE activation_codes 
        SET is_used = true, used_by_machine = ${machineCode}, used_at = ${serverTime}
        WHERE code = ${activationCode}
      `;

            return new Response(JSON.stringify({
                success: true,
                message: 'SUCCESS'
            }), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
            });
        }

        return new Response(JSON.stringify({ error: 'Invalid action' }), {
            status: 400,
            headers: { 'Content-Type': 'application/json' },
        });

    } catch (error) {
        console.error('Error:', error);
        return new Response(JSON.stringify({
            error: 'Internal server error',
            details: error.message
        }), {
            status: 500,
            headers: { 'Content-Type': 'application/json' },
        });
    }
}
