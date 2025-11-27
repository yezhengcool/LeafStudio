/**
 * Cloudflare Worker API for LeafStudio Activation
 * 部署到 Cloudflare Workers 或 Vercel Edge Functions
 */

// 导入 Neon serverless driver
import { neon } from '@neondatabase/serverless';

// 数据库连接
const DATABASE_URL = 'postgresql://neondb_owner:npg_kf5BO3mHDoTZ@ep-sparkling-river-ah52my74-pooler.c-3.us-east-1.aws.neon.tech/neondb?sslmode=require';
const sql = neon(DATABASE_URL);

/**
 * 执行 SQL 查询 - 使用真实的 PostgreSQL 数据库
 */
async function query(queryText, params = []) {
    try {
        // 使用 Neon serverless driver 执行查询
        // 参数化查询格式：SELECT * FROM table WHERE id = $1
        const result = await sql(queryText, params);
        return { rows: result };
    } catch (error) {
        console.error('Database query error:', error);
        throw error;
    }
}

/**
 * 检查激活状态
 */
async function checkActivation(machineCode) {
    try {
        // 获取服务器时间
        const timeResult = await query("SELECT EXTRACT(EPOCH FROM NOW()) * 1000 AS server_time");
        const serverTime = parseInt(timeResult.rows[0].server_time);

        // 查询设备记录
        const deviceResult = await query(
            "SELECT expiry_time FROM activation_records WHERE machine_code = $1",
            [machineCode]
        );

        if (deviceResult.rows.length === 0) {
            // 设备不存在，自动注册（3天试用）
            const trialDays = 3;
            const expiryTime = serverTime + (trialDays * 24 * 60 * 60 * 1000);

            await query(
                "INSERT INTO activation_records (machine_code, activation_code, activation_time, expiry_time, device_note) VALUES ($1, $2, $3, $4, $5)",
                [machineCode, 'TRIAL-AUTO', serverTime, expiryTime, '自动注册试用']
            );

            return {
                isValid: true,
                remainingSeconds: Math.floor((expiryTime - serverTime) / 1000),
                expiryTime: expiryTime,
                message: `试用期剩余 ${trialDays} 天`
            };
        }

        // 设备已存在
        const expiryTime = parseInt(deviceResult.rows[0].expiry_time);
        const remainingMillis = expiryTime - serverTime;
        const isValid = remainingMillis > 0;

        return {
            isValid: isValid,
            remainingSeconds: isValid ? Math.floor(remainingMillis / 1000) : 0,
            expiryTime: expiryTime,
            message: isValid ? '已激活' : '激活已过期'
        };

    } catch (error) {
        console.error('Check activation error:', error);
        throw error;
    }
}

/**
 * 激活设备
 */
async function activateDevice(machineCode, activationCode) {
    try {
        // 验证激活码
        const codeResult = await query(
            "SELECT * FROM activation_codes WHERE code = $1",
            [activationCode]
        );

        if (codeResult.rows.length === 0) {
            return { success: false, message: '激活码无效' };
        }

        const codeInfo = codeResult.rows[0];
        if (codeInfo.is_used) {
            return { success: false, message: '激活码已被使用' };
        }

        const durationDays = parseInt(codeInfo.duration_days);

        // 获取服务器时间
        const timeResult = await query("SELECT EXTRACT(EPOCH FROM NOW()) * 1000 AS server_time");
        const serverTime = parseInt(timeResult.rows[0].server_time);

        // 查询设备是否存在
        const deviceResult = await query(
            "SELECT expiry_time FROM activation_records WHERE machine_code = $1",
            [machineCode]
        );

        const durationMillis = durationDays * 24 * 60 * 60 * 1000;
        let newExpiryTime;

        if (deviceResult.rows.length > 0) {
            // 续费
            const currentExpiry = parseInt(deviceResult.rows[0].expiry_time);
            newExpiryTime = (currentExpiry > serverTime ? currentExpiry : serverTime) + durationMillis;

            await query(
                "UPDATE activation_records SET expiry_time = $1, activation_code = $2, updated_at = NOW() WHERE machine_code = $3",
                [newExpiryTime, activationCode, machineCode]
            );
        } else {
            // 新激活
            newExpiryTime = serverTime + durationMillis;
            await query(
                "INSERT INTO activation_records (machine_code, activation_code, activation_time, expiry_time) VALUES ($1, $2, $3, $4)",
                [machineCode, activationCode, serverTime, newExpiryTime]
            );
        }

        // 标记激活码已使用
        await query(
            "UPDATE activation_codes SET is_used = true, used_by_machine = $1, used_at = $2 WHERE code = $3",
            [machineCode, serverTime, activationCode]
        );

        return { success: true, message: '激活成功' };

    } catch (error) {
        console.error('Activate device error:', error);
        return { success: false, message: `激活失败: ${error.message}` };
    }
}

/**
 * 主处理函数
 */
export default {
    async fetch(request, env, ctx) {
        // CORS 处理
        const corsHeaders = {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type',
        };

        if (request.method === 'OPTIONS') {
            return new Response(null, { headers: corsHeaders });
        }

        if (request.method !== 'POST') {
            return new Response(JSON.stringify({ error: 'Method not allowed' }), {
                status: 405,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' }
            });
        }

        try {
            const body = await request.json();
            const { action, machineCode, activationCode } = body;

            if (!action || !machineCode) {
                return new Response(JSON.stringify({ error: 'Missing required parameters' }), {
                    status: 400,
                    headers: { ...corsHeaders, 'Content-Type': 'application/json' }
                });
            }

            // 检查激活状态
            if (action === 'check') {
                const result = await checkActivation(machineCode);
                return new Response(JSON.stringify(result), {
                    headers: { ...corsHeaders, 'Content-Type': 'application/json' }
                });
            }

            // 激活设备
            if (action === 'activate') {
                if (!activationCode) {
                    return new Response(JSON.stringify({ error: 'Missing activation code' }), {
                        status: 400,
                        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
                    });
                }
                const result = await activateDevice(machineCode, activationCode);
                return new Response(JSON.stringify(result), {
                    headers: { ...corsHeaders, 'Content-Type': 'application/json' }
                });
            }

            return new Response(JSON.stringify({ error: 'Invalid action' }), {
                status: 400,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' }
            });

        } catch (error) {
            console.error('Error:', error);
            return new Response(JSON.stringify({ error: error.message }), {
                status: 500,
                headers: { ...corsHeaders, 'Content-Type': 'application/json' }
            });
        }
    }
};
