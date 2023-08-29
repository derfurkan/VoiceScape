/*
 * Copyright (c) 2019, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.furkan.voicescape;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

@Slf4j
public class RedisPool
{
    private final String redisHost;
    private final BlockingQueue<Jedis> queue;

    RedisPool(int queueSize, String redisHost, int redisPort)
    {
        this.redisHost = redisHost;

        queue = new ArrayBlockingQueue<>(queueSize);
        for (int i = 0; i < queueSize; ++i)
        {
            Jedis jedis = new PooledJedis(redisHost, redisPort);
            queue.offer(jedis);
        }
    }

    RedisPool(int queueSize, String redisHost, int redisPort, String username, String password)
    {
        this.redisHost = redisHost;

        queue = new ArrayBlockingQueue<>(queueSize);
        for (int i = 0; i < queueSize; ++i)
        {
            Jedis jedis = new PooledJedis(redisHost, redisPort, username, password);
            queue.offer(jedis);
        }
    }

    public Jedis getResource()
    {
        Jedis jedis;
        try
        {
            jedis = queue.poll(1, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        if (jedis == null)
        {
            throw new RuntimeException("Unable to acquire connection from pool, timeout");
        }
        return jedis;
    }

    class PooledJedis extends Jedis
    {



        PooledJedis(String host, int port)
        {
            super(host, port);
            super.auth("", "");
        }

        PooledJedis(String host, int port, String username, String password)
        {
            super(host, port);
            super.auth(username, password);
        }

        @Override
        public void close()
        {
            if (!getClient().isBroken())
            {
                queue.offer(this);
                return;
            }

            try
            {
                super.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            queue.clear();

        }
    }
}