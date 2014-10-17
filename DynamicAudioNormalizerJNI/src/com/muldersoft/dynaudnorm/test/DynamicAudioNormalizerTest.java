//////////////////////////////////////////////////////////////////////////////////
// Dynamic Audio Normalizer - Java/JNI Wrapper
// Copyright (c) 2014 LoRd_MuldeR <mulder2@gmx.de>. Some rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
//
// http://opensource.org/licenses/MIT
//////////////////////////////////////////////////////////////////////////////////

package com.muldersoft.dynaudnorm.test;

import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.muldersoft.dynaudnorm.JDynamicAudioNormalizer;
import com.muldersoft.dynaudnorm.JDynamicAudioNormalizer.Logger;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DynamicAudioNormalizerTest
{
	//------------------------------------------------------------------------------------------------
	// PCM File Reader
	//------------------------------------------------------------------------------------------------

	static class PCMFileReader
	{
		private static final int FRAME_SIZE = 4;
		private final BufferedInputStream inputStream;
		private byte [] tempBuffer = null;
		
		public PCMFileReader(final String fileName) throws IOException
		{
			inputStream = new BufferedInputStream(new FileInputStream(new File(fileName)));
		}
		
		public int read(double [][] buffer) throws IOException
		{
			if(buffer.length != 2)
			{
				throw new RuntimeException("Output array dimension must be two!");
			}
			
			final int maxReadSize = buffer[0].length * FRAME_SIZE;
			if((tempBuffer == null) || (tempBuffer.length < maxReadSize))
			{
				tempBuffer = new byte[maxReadSize];
			}
			
			final int sampleCount = inputStream.read(tempBuffer, 0, maxReadSize) / FRAME_SIZE;
			
			if(sampleCount > 0)
			{
				ByteBuffer byteBuffer = ByteBuffer.wrap(tempBuffer);
				byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
				for(int i = 0; i < sampleCount; i++)
				{
					for(int c = 0; c < 2; c++)
					{
						buffer[c][i] = ShortToDouble(byteBuffer.getShort());
					}
				}
			}
			
			return sampleCount;
		}
		
		private double ShortToDouble(final short x)
		{
			return ((double)x) / ((double)Short.MAX_VALUE);
		}
	}
	
	//------------------------------------------------------------------------------------------------
	// PCM File Writer
	//------------------------------------------------------------------------------------------------

	static class PCMFileWriter
	{
		private static final int FRAME_SIZE = 4;
		private final BufferedOutputStream outputStream;
		private byte [] tempBuffer = null;
		
		public PCMFileWriter(final String fileName) throws IOException
		{
			outputStream = new BufferedOutputStream(new FileOutputStream(new File(fileName)));
		}
		
		public void write(double [][] buffer, final int sampleCount) throws IOException
		{
			if(buffer.length != 2)
			{
				throw new IOException("Input array dimension must be two!");
			}
			
			final int writeSize = sampleCount * FRAME_SIZE;
			if((tempBuffer == null) || (tempBuffer.length < writeSize))
			{
				tempBuffer = new byte[writeSize];
			}
			
			if(sampleCount > 0)
			{
				ByteBuffer byteBuffer = ByteBuffer.wrap(tempBuffer);
				byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
				for(int i = 0; i < sampleCount; i++)
				{
					for(int c = 0; c < 2; c++)
					{
						byteBuffer.putShort(doubleToShort(buffer[c][i]));
					}
				}
			}
			
			outputStream.write(tempBuffer, 0, writeSize);
		}
		
		private short doubleToShort(final double x)
		{
			return (short) Math.round(Math.max(-1.0,  Math.min(1.0, x)) * ((double)Short.MAX_VALUE));
		}
		
		public void flush() throws IOException
		{
			outputStream.flush();
		}
	}
	
	//------------------------------------------------------------------------------------------------
	// Test Functions
	//------------------------------------------------------------------------------------------------

	@Before
	public void setUp()
	{
		System.out.println("============================= TEST BEGIN =============================");
	}
	
	@After
	public void shutDown()
	{
		System.out.println("============================= TEST DONE! =============================");
		System.out.println();
	}

	@Test
	public void test1_SetLoggingHandler()
	{
		JDynamicAudioNormalizer.setLoggingHandler(new Logger()
		{
			public void log(int level, String message) {
				System.err.println("[JDynAudNorm] " + message);
			}
		});
		System.out.println("Message handler installed successfully.");
	}

	@Test
	public void test2_GetVersionInfo()
	{
		final int[] versionInfo = JDynamicAudioNormalizer.getVersionInfo();
		System.out.println("Library Version: " + versionInfo[0] + "." + versionInfo[1] + "-" + versionInfo[2]);
	}
	
	@Test
	public void test3_GetBuildInfo()
	{
		final Map<String,String> buildInfo = JDynamicAudioNormalizer.getBuildInfo();
		for(final String key : buildInfo.keySet())
		{
			System.out.println("Build Info: " + key + "=" + buildInfo.get(key));
		}
	}
	
	@Test
	public void test4_ConstructorAndRelease()
	{
		for(int i = 0; i < 128; i++)
		{
			Queue<JDynamicAudioNormalizer> instances = new LinkedList<JDynamicAudioNormalizer>();

			// Constructor
			for(int k = 0; k < 32; k++)
			{
				JDynamicAudioNormalizer instance = new JDynamicAudioNormalizer(2, 44100, 500, 31, 0.95, 10.0, 0.0, 0.0, true, false, false);
				instances.add(instance);
			}
			
			// Release
			while(!instances.isEmpty())
			{
				instances.poll().release();
			}
		}
	}
	
	@Test
	public void test5_ProcessAudioFile()
	{
		PCMFileReader reader = null;
		try
		{
			reader = new PCMFileReader("Input.pcm");
		}
		catch (Exception e)
		{
			fail("Failed to open ionput audio file!");
		}
		
		PCMFileWriter writer = null;
		try
		{
			writer = new PCMFileWriter("Output.pcm");
		}
		catch (Exception e)
		{
			fail("Failed to open ionput audio file!");
		}
		
		double [][] sampleBuffer = new double[2][4096];
		
		for(;;)
		{
			int sampleCount = 0;
			try
			{
				sampleCount = reader.read(sampleBuffer);
			}
			catch(IOException e)
			{
				fail("Failed to read from input file!");
			}
			
			if(sampleCount > 0)
			{
				for(int i = 0; i < sampleCount; i++)
				{
					sampleBuffer[0][i] = sampleBuffer[0][i] * 0.666;
					sampleBuffer[1][i] = sampleBuffer[1][i] * 1.333;
				}
				
				try
				{
					writer.write(sampleBuffer, sampleCount);
				}
				catch (IOException e)
				{
					fail("Failed to write to output file!");
				}
			}

			if(sampleCount < sampleBuffer[0].length)
			{
				try
				{
					writer.flush();
				}
				catch (IOException e)
				{
					fail("Failed to write to output file!");
				}
				break; /*EOF reached*/
			}
		}
	}
}
