/*
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.runtime.pipeline.impl;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderError;
import org.drools.builder.KnowledgeBuilderErrors;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.io.ResourceFactory;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.pipeline.Action;
import org.drools.runtime.pipeline.Pipeline;
import org.drools.runtime.pipeline.PipelineFactory;
import org.drools.runtime.pipeline.ResultHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class StatefulKnowledgeSessionPipelineTest {
    @Test
    public void testInsertObject() {
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();
        
        Action executeResultHandler = PipelineFactory.newExecuteResultHandler();
        
        StatefulKnowledgeSessionInsertStage insertStage = new StatefulKnowledgeSessionInsertStage();
        insertStage.setReceiver( executeResultHandler );
        
        
        Pipeline pipeline = PipelineFactory.newStatefulKnowledgeSessionPipeline(ksession);
        pipeline.setReceiver( insertStage );
        
        assertEquals( 0, ksession.getObjects().size() );
        
        ResultHandlerImpl resultHanadle = new ResultHandlerImpl();
        pipeline.insert( "Hello", resultHanadle );
        
        assertEquals( 1, resultHanadle.getHandles().size() );
    }
    
    @Test
    public void testInsertObject2() {
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();
        
        org.drools.runtime.pipeline.KnowledgeRuntimeCommand insertStage = PipelineFactory.newStatefulKnowledgeSessionInsert();
        ExecuteResultHandler resultHandlerStage = new ExecuteResultHandler();
        insertStage.setReceiver( resultHandlerStage );
        
        Pipeline pipeline = PipelineFactory.newStatefulKnowledgeSessionPipeline(ksession);
        pipeline.setReceiver( insertStage );
        
        assertEquals( 0, ksession.getObjects().size() );
        
        ResultHandlerImpl resultHanadle = new ResultHandlerImpl();
        pipeline.insert( "Hello", resultHanadle );
        
        assertEquals( 1, resultHanadle.getHandles().size() );
    }
    
    @Test
    public void testStartProcess() {
        // This also tests setGlobal
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        
        Reader source = new StringReader(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<process xmlns=\"http://drools.org/drools-5.0/process\"\n" +
            "         xmlns:xs=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xs:schemaLocation=\"http://drools.org/drools-5.0/process drools-processes-5.0.xsd\"\n" +
            "         type=\"RuleFlow\" name=\"flow\" id=\"org.drools.actions\" package-name=\"org.drools\" version=\"1\" >\n" +
            "\n" +
            "  <header>\n" +
            "    <globals>\n" +
            "      <global identifier=\"list\" type=\"java.util.List\" />\n" +
            "    </globals>\n" +
            "  </header>\n" +
            "\n" +
            "  <nodes>\n" +
            "    <start id=\"1\" name=\"Start\" />\n" +
            "    <actionNode id=\"2\" name=\"MyActionNode\" >\n" +
            "      <action type=\"expression\" dialect=\"java\" >" +
            "        String variable = (String) context.getVariable(\"variable\");\n" +
            "        list.add(variable);\n" +
            "       </action>\n" +
            "    </actionNode>\n" + 
            "    <end id=\"3\" name=\"End\" />\n" +
            "  </nodes>\n" +
            "\n" +
            "  <connections>\n" +
            "    <connection from=\"1\" to=\"2\" />\n" +
            "    <connection from=\"2\" to=\"3\" />\n" +
            "  </connections>\n" +
            "\n" +
            "</process>");
        
        kbuilder.add( ResourceFactory.newReaderResource( source ), ResourceType.DRF );
        KnowledgeBuilderErrors errors = kbuilder.getErrors();
        if (!errors.isEmpty()) {
            for (KnowledgeBuilderError error: errors) {
                System.err.println(error);
            }
            fail("Errors while building package");
        }
        
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages( kbuilder.getKnowledgePackages() );
        
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();
        List<String> list = new ArrayList<String>();
        Map globals = new HashMap();
        globals.put("list", list);
        
        StatefulKnowledgeSessionSetGlobalStage setGlobal = new StatefulKnowledgeSessionSetGlobalStage();
        StatefulKnowledgeSessionPipelineImpl pipeline = new StatefulKnowledgeSessionPipelineImpl(ksession);
        pipeline.setReceiver( setGlobal );
        pipeline.insert( globals, null );
        
        Map vars = new HashMap();
        vars.put( "variable", "SomeText" );
        
        StatefulKnowledgeSessionStartProcessStage startProcess = new StatefulKnowledgeSessionStartProcessStage("org.drools.actions");
        pipeline = new StatefulKnowledgeSessionPipelineImpl(ksession);
        pipeline.setReceiver( startProcess );
        pipeline.insert( vars, null );
        
        assertEquals(1, list.size());
        assertEquals("SomeText", list.get(0));
    }
    
    @Test
    public void testSignalEvent() throws Exception {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        Reader source = new StringReader(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<process xmlns=\"http://drools.org/drools-5.0/process\"\n" +
            "         xmlns:xs=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "         xs:schemaLocation=\"http://drools.org/drools-5.0/process drools-processes-5.0.xsd\"\n" +
            "         type=\"RuleFlow\" name=\"flow\" id=\"org.drools.event\" package-name=\"org.drools\" version=\"1\" >\n" +
            "\n" +
            "  <header>\n" +
            "    <globals>\n" +
            "      <global identifier=\"list\" type=\"java.util.List\" />\n" +
            "    </globals>\n" +            
            "    <variables>\n" +
            "      <variable name=\"MyVar\" >\n" +
            "        <type name=\"org.drools.process.core.datatype.impl.type.StringDataType\" />\n" +
            "        <value>SomeText</value>\n" +
            "      </variable>\n" +
            "    </variables>\n" +
            "  </header>\n" +
            "\n" +
            "  <nodes>\n" +
            "    <start id=\"1\" name=\"Start\" />\n" +
            "    <eventNode id=\"2\" name=\"Event\" variableName=\"MyVar\" >\n" +
            "      <eventFilters>\n" +
            "        <eventFilter type=\"eventType\" eventType=\"MyEvent\" />\n" +
            "      </eventFilters>\n" +
            "    </eventNode>\n" +
            "    <actionNode id=\"3\" name=\"MyActionNode\" >\n" +
            "      <action type=\"expression\" dialect=\"java\" >" +
            "        String variable = (String) context.getVariable(\"MyVar\");\n" +
            "        list.add(variable);\n" +
            "       </action>\n" +
            "    </actionNode>\n" + 
            "    <join id=\"4\" name=\"Join\" type=\"1\" />\n" +
            "    <end id=\"5\" name=\"End\" />\n" +
            "  </nodes>\n" +
            "\n" +
            "  <connections>\n" +
            "    <connection from=\"1\" to=\"4\" />\n" +
            "    <connection from=\"2\" to=\"3\" />\n" +
            "    <connection from=\"3\" to=\"4\" />\n" +
            "    <connection from=\"4\" to=\"5\" />\n" +
            "  </connections>\n" +
            "\n" +
            "</process>");
        kbuilder.add( ResourceFactory.newReaderResource( source ), ResourceType.DRF );
        KnowledgeBuilderErrors errors = kbuilder.getErrors();
        if (!errors.isEmpty()) {
            for (KnowledgeBuilderError error: errors) {
                System.err.println(error);
            }
            fail("Errors while building package");
        }
        
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages( kbuilder.getKnowledgePackages() );
        
        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();
        List<String> list = new ArrayList<String>();
        Map globals = new HashMap();
        globals.put("list", list);
        
        StatefulKnowledgeSessionSetGlobalStage setGlobal = new StatefulKnowledgeSessionSetGlobalStage();
        StatefulKnowledgeSessionPipelineImpl pipeline = new StatefulKnowledgeSessionPipelineImpl(ksession);
        pipeline.setReceiver( setGlobal );
        pipeline.insert( globals, null );
        
        org.drools.runtime.process.ProcessInstance instance = ksession.startProcess("org.drools.event");
        
        StatefulKnowledgeSessionSignalEventStage signalEvent = new StatefulKnowledgeSessionSignalEventStage( "MyEvent",
                                                                                                              instance.getId());
        pipeline = new StatefulKnowledgeSessionPipelineImpl(ksession);
        pipeline.setReceiver( signalEvent );
        
        pipeline.insert( "MyValue", null );
        
        assertEquals(1, list.size());
        assertEquals("MyValue", list.get(0));
        
    }
    
    public static class ResultHandlerImpl implements ResultHandler {
        Map handles;
        public void handleResult(Object object) {
           this.handles = ( Map ) object;
        }
        public Map getHandles() {
            return this.handles;
        }
        
    }
}
