/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.runtime.datastream

import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.util.StreamingMultipleProgramsTestBase
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.api.scala._
import org.apache.flink.table.api.scala.stream.utils.{StreamITCase, StreamTestData}
import org.apache.flink.table.expressions.utils.{RichFunc1, RichFunc2}
import org.apache.flink.table.utils.UserDefinedFunctionTestUtils
import org.apache.flink.types.Row
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

class DataStreamCalcITCase extends StreamingMultipleProgramsTestBase {

  @Test
  def testUserDefinedFunctionWithParameter(): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)
    tEnv.registerFunction("RichFunc2", new RichFunc2)
    UserDefinedFunctionTestUtils.setJobParameters(env, Map("string.value" -> "ABC"))

    StreamITCase.testResults = mutable.MutableList()

    val result = StreamTestData.get3TupleDataStream(env)
      .toTable(tEnv, 'a, 'b, 'c)
      .where("RichFunc2(c)='ABC#Hello'")
      .select('c)

    val results = result.toAppendStream[Row]
    results.addSink(new StreamITCase.StringSink[Row])
    env.execute()

    val expected = mutable.MutableList("Hello")
    assertEquals(expected.sorted, StreamITCase.testResults.sorted)
  }

  @Test
  def testMultipleUserDefinedFunctions(): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)
    tEnv.registerFunction("RichFunc1", new RichFunc1)
    tEnv.registerFunction("RichFunc2", new RichFunc2)
    UserDefinedFunctionTestUtils.setJobParameters(env, Map("string.value" -> "Abc"))

    StreamITCase.testResults = mutable.MutableList()

    val result = StreamTestData.get3TupleDataStream(env)
      .toTable(tEnv, 'a, 'b, 'c)
      .where("RichFunc2(c)='Abc#Hello' || RichFunc1(a)=3 && b=2")
      .select('c)

    val results = result.toAppendStream[Row]
    results.addSink(new StreamITCase.StringSink[Row])
    env.execute()

    val expected = mutable.MutableList("Hello", "Hello world")
    assertEquals(expected.sorted, StreamITCase.testResults.sorted)
  }

  @Test
  def testUnionWithAnyType(): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getTableEnvironment(env)

    StreamITCase.testResults = mutable.MutableList()
    val s1 = env.fromElements((1, new NonPojo), (2, new NonPojo)).toTable(tEnv, 'a, 'b)
    val s2 = env.fromElements((3, new NonPojo), (4, new NonPojo)).toTable(tEnv, 'a, 'b)

    val result = s1.unionAll(s2).toAppendStream[Row]
    result.addSink(new StreamITCase.StringSink[Row])
    env.execute()

    val expected = mutable.MutableList("1,{}", "2,{}", "3,{}", "4,{}")
    assertEquals(expected.sorted, StreamITCase.testResults.sorted)
  }

  class NonPojo {
    val x = new java.util.HashMap[String, String]()

    override def toString: String = x.toString
  }
}