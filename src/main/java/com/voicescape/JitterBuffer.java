package com.voicescape;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JitterBuffer
{
	private static final int BUFFER_SIZE = 16;
	private static final int MASK = BUFFER_SIZE - 1;

	private static final int PREBUFFER_FRAMES = 2;

	private final byte[][] slots = new byte[BUFFER_SIZE][];
	private final boolean[] occupied = new boolean[BUFFER_SIZE];
	private int nextReadSeq = -1;
	private int maxReceivedSeq = -1;
	private boolean primed = false;
	private boolean wasReset = false;

	public synchronized void put(int sequenceNumber, byte[] opusPayload)
	{
		if (nextReadSeq == -1)
		{
			nextReadSeq = sequenceNumber;
		}

		if (sequenceNumber < nextReadSeq)
		{
			return;
		}

		if (sequenceNumber >= nextReadSeq + BUFFER_SIZE)
		{
			for (int i = 0; i < BUFFER_SIZE; i++)
			{
				slots[i] = null;
				occupied[i] = false;
			}
			nextReadSeq = sequenceNumber;
			maxReceivedSeq = -1;
			primed = false;
			wasReset = true;
		}

		int idx = sequenceNumber & MASK;
		slots[idx] = opusPayload;
		occupied[idx] = true;

		if (sequenceNumber > maxReceivedSeq)
		{
			maxReceivedSeq = sequenceNumber;
		}
	}

	public synchronized boolean isReady()
	{
		if (primed)
		{
			return true;
		}
		if (bufferedCount() >= PREBUFFER_FRAMES)
		{
			primed = true;
			return true;
		}
		return false;
	}

	public synchronized boolean canPoll()
	{
		if (nextReadSeq == -1)
		{
			return false;
		}
		int idx = nextReadSeq & MASK;
		if (occupied[idx])
		{
			return true;
		}
		return nextReadSeq < maxReceivedSeq;
	}

	public synchronized byte[] poll()
	{
		if (nextReadSeq == -1)
		{
			return null;
		}

		int idx = nextReadSeq & MASK;
		if (occupied[idx])
		{
			byte[] data = slots[idx];
			slots[idx] = null;
			occupied[idx] = false;
			nextReadSeq++;
			return data;
		}

		if (nextReadSeq < maxReceivedSeq)
		{
			nextReadSeq++;
			return null; // PLC
		}
		return null;
	}

	public synchronized boolean consumeReset()
	{
		if (wasReset)
		{
			wasReset = false;
			return true;
		}
		return false;
	}

	public synchronized void reset()
	{
		for (int i = 0; i < BUFFER_SIZE; i++)
		{
			slots[i] = null;
			occupied[i] = false;
		}
		nextReadSeq = -1;
		maxReceivedSeq = -1;
		primed = false;
	}

	public synchronized int bufferedCount()
	{
		int count = 0;
		for (boolean b : occupied)
		{
			if (b) count++;
		}
		return count;
	}
}
