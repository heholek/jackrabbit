/*
 * Copyright 2004 The Apache Software Foundation.
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
package org.apache.jackrabbit.test.observation;

import javax.jcr.RepositoryException;
import javax.jcr.Node;
import javax.jcr.observation.EventType;
import javax.jcr.observation.Event;

/**
 * Test cases for {@link javax.jcr.observation.EventType#PROPERTY_CHANGED
 * PROPERTY_CHANGED} events.
 *
 * @author Marcel Reutegger
 * @version $Revision:  $, $Date:  $
 */
public class PropertyChangedTest extends AbstractObservationTest {

    public void testSinglePropertyChanged() throws RepositoryException {
        EventResult result = new EventResult(log);
        Node foo = testRoot.addNode("foo", NT_UNSTRUCTURED);
	foo.setProperty("bar", new String[] { "foo" });
        testRoot.save();
	addEventListener(result, EventType.PROPERTY_CHANGED);
	foo.getProperty("bar").setValue(new String[] { "foobar" });
	testRoot.save();
        removeEventListener(result);
        Event[] events = result.getEvents(DEFAULT_WAIT_TIMEOUT);
	checkPropertyChanged(events, new String[] { "foo/bar" });
    }

    public void testMultiPropertyChanged() throws RepositoryException {
        EventResult result = new EventResult(log);
        Node foo = testRoot.addNode("foo", NT_UNSTRUCTURED);
	foo.setProperty("prop1", new String[] { "foo" });
	foo.setProperty("prop2", new String[] { "bar" });
        testRoot.save();
	addEventListener(result, EventType.PROPERTY_CHANGED);
	foo.getProperty("prop1").setValue(new String[] { "foobar" });
	foo.getProperty("prop2").setValue(new String[] { "foobar" });
	testRoot.save();
        removeEventListener(result);
        Event[] events = result.getEvents(DEFAULT_WAIT_TIMEOUT);
	checkPropertyChanged(events, new String[] { "foo/prop1", "foo/prop2" });
    }

    public void testSinglePropertyChangedWithAdded() throws RepositoryException {
        EventResult result = new EventResult(log);
        Node foo = testRoot.addNode("foo", NT_UNSTRUCTURED);
	foo.setProperty("bar", new String[] { "foo" });
        testRoot.save();
	addEventListener(result, EventType.PROPERTY_CHANGED);
	foo.getProperty("bar").setValue(new String[] { "foobar" });
	foo.setProperty("foo", new String[] { "bar" });    // will not fire prop changed event
	testRoot.save();
        removeEventListener(result);
        Event[] events = result.getEvents(DEFAULT_WAIT_TIMEOUT);
	checkPropertyChanged(events, new String[] { "foo/bar" });
    }

    public void testMultiPropertyChangedWithAdded() throws RepositoryException {
        EventResult result = new EventResult(log);
        Node foo = testRoot.addNode("foo", NT_UNSTRUCTURED);
	foo.setProperty("prop1", new String[] { "foo" });
	foo.setProperty("prop2", new String[] { "bar" });
        testRoot.save();
	addEventListener(result, EventType.PROPERTY_CHANGED);
	foo.getProperty("prop1").setValue(new String[] { "foobar" });
	foo.getProperty("prop2").setValue(new String[] { "foobar" });
	foo.setProperty("prop3", new String[] { "foo" }); // will not fire prop changed event
	testRoot.save();
        removeEventListener(result);
        Event[] events = result.getEvents(DEFAULT_WAIT_TIMEOUT);
	checkPropertyChanged(events, new String[] { "foo/prop1", "foo/prop2" });
    }



}
