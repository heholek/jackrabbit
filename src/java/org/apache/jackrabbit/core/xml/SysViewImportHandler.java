/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
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
package org.apache.jackrabbit.core.xml;

import org.apache.jackrabbit.core.IllegalNameException;
import org.apache.jackrabbit.core.NamespaceResolver;
import org.apache.jackrabbit.core.QName;
import org.apache.jackrabbit.core.UnknownPrefixException;
import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.jcr.InvalidSerializedDataException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;

/**
 * <code>SysViewImportHandler</code>  ...
 */
class SysViewImportHandler extends TargetImportHandler {

    private static Logger log = Logger.getLogger(SysViewImportHandler.class);

    /**
     * stack of ImportState instances; an instance is pushed onto the stack
     * in the startElement method every time a sv:node element is encountered;
     * the same instance is popped from the stack in the endElement method
     * when the corresponding sv:node element is encountered.
     */
    private final Stack stack = new Stack();

    /**
     * fields used temporarily while processing sv:property and sv:value elements
     */
    private QName currentPropName;
    private int currentPropType = PropertyType.UNDEFINED;
    // list of AppendableValue objects
    private ArrayList currentPropValues = new ArrayList();
    private AppendableValue currentPropValue;

    /**
     * Constructs a new <code>SysViewImportHandler</code>.
     *
     * @param importer
     * @param nsContext
     */
    SysViewImportHandler(Importer importer, NamespaceResolver nsContext) {
        super(importer, nsContext);
    }

    private void processNode(ImportState state, boolean start, boolean end)
            throws SAXException {
        if (!start && !end) {
            return;
        }
        Importer.NodeInfo node = new Importer.NodeInfo();
        node.setName(state.nodeName);
        node.setNodeTypeName(state.nodeTypeName);
        if (state.mixinNames != null) {
            QName[] mixins = (QName[]) state.mixinNames.toArray(new QName[state.mixinNames.size()]);
            node.setMixinNames(mixins);
        }
        node.setUUID(state.uuid);
        // call Importer
        try {
            if (start) {
                importer.startNode(node, state.props, nsContext);
                // dispose temporary property values
                for (Iterator iter = state.props.iterator(); iter.hasNext();) {
                    Importer.PropInfo pi = (Importer.PropInfo) iter.next();
                    disposePropertyValues(pi);
                }

            }
            if (end) {
                importer.endNode(node);
            }
        } catch (RepositoryException re) {
            throw new SAXException(re);
        }
    }

    //-------------------------------------------------------< ContentHandler >
    /**
     * {@inheritDoc}
     */
    public void startDocument() throws SAXException {
        try {
            importer.start();
        } catch (RepositoryException re) {
            throw new SAXException(re);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void startElement(String namespaceURI, String localName,
                             String qName, Attributes atts)
            throws SAXException {
        // check namespace
        if (!NS_SV_URI.equals(namespaceURI)) {
            throw new SAXException(new InvalidSerializedDataException("invalid namespace for element in system view xml document: "
                    + namespaceURI));
        }
        // check element name
        if (SysViewSAXEventGenerator.NODE_ELEMENT.equals(localName)) {
            // sv:node element

            // node name (value of sv:name attribute)
            String name = atts.getValue(SysViewSAXEventGenerator.PREFIXED_NAME_ATTRIBUTE);
            if (name == null) {
                throw new SAXException(new InvalidSerializedDataException("missing mandatory sv:name attribute of element sv:node"));
            }

            if (!stack.isEmpty()) {
                // process current node first
                ImportState current = (ImportState) stack.peek();
                // need to start current node
                if (!current.started) {
                    processNode(current, true, false);
                    current.started = true;
                }
            }

            // push new ImportState instance onto the stack
            ImportState state = new ImportState();
            try {
                state.nodeName = QName.fromJCRName(name, nsContext);
            } catch (IllegalNameException ine) {
                throw new SAXException(new InvalidSerializedDataException("illegal node name: " + name, ine));
            } catch (UnknownPrefixException upe) {
                throw new SAXException(new InvalidSerializedDataException("illegal node name: " + name, upe));
            }
            stack.push(state);
        } else if (SysViewSAXEventGenerator.PROPERTY_ELEMENT.equals(localName)) {
            // sv:property element

            // reset temp fields
            currentPropValues.clear();

            // property name (value of sv:name attribute)
            String name = atts.getValue(SysViewSAXEventGenerator.PREFIXED_NAME_ATTRIBUTE);
            if (name == null) {
                throw new SAXException(new InvalidSerializedDataException("missing mandatory sv:name attribute of element sv:property"));
            }
            try {
                currentPropName = QName.fromJCRName(name, nsContext);
            } catch (IllegalNameException ine) {
                throw new SAXException(new InvalidSerializedDataException("illegal property name: " + name, ine));
            } catch (UnknownPrefixException upe) {
                throw new SAXException(new InvalidSerializedDataException("illegal property name: " + name, upe));
            }
            // property type (sv:type attribute)
            String type = atts.getValue(SysViewSAXEventGenerator.PREFIXED_TYPE_ATTRIBUTE);
            if (type == null) {
                throw new SAXException(new InvalidSerializedDataException("missing mandatory sv:type attribute of element sv:property"));
            }
            currentPropType = PropertyType.valueFromName(type);
        } else if (SysViewSAXEventGenerator.VALUE_ELEMENT.equals(localName)) {
            // sv:value element

            // reset temp fields
            if (currentPropType == PropertyType.BINARY) {
                // binary value; use temp-file backed value appender
                try {
                    currentPropValue = new CLOBValue();
                } catch (IOException ioe) {
                    String msg = "error while processing property value "
                            + currentPropName;
                    log.debug(msg, ioe);
                    throw new SAXException(msg, ioe);
                }
            } else {
                // 'normal' value; use StringBuffer-backed value appender
                currentPropValue = new StringBufferValue();
            }
        } else {
            throw new SAXException(new InvalidSerializedDataException("unexpected element found in system view xml document: "
                    + localName));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (currentPropValue != null) {
            // property value (character data of sv:value element)
            try {
                currentPropValue.append(ch, start, length);
            } catch (IOException ioe) {
                throw new SAXException("error while processing property value",
                        ioe);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void ignorableWhitespace(char ch[], int start, int length)
            throws SAXException {
        if (currentPropValue != null) {
            // property value

            // data reported by the ignorableWhitespace event within
            // sv:value tags is considered part of the value
            try {
                currentPropValue.append(ch, start, length);
            } catch (IOException ioe) {
                throw new SAXException("error while processing property value",
                        ioe);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {
        // check element name
        ImportState state = (ImportState) stack.peek();
        if (SysViewSAXEventGenerator.NODE_ELEMENT.equals(localName)) {
            // sv:node element
            if (!state.started) {
                // need to start & end current node
                processNode(state, true, true);
                state.started = true;
            } else {
                // need to end current node
                processNode(state, false, true);
            }
            // pop current state from stack
            stack.pop();
        } else if (SysViewSAXEventGenerator.PROPERTY_ELEMENT.equals(localName)) {
            // sv:property element

            // check if all system properties (jcr:primaryType, jcr:uuid etc.)
            // have been collected and create node as necessary
            if (currentPropName.equals(JCR_PRIMARYTYPE)) {
                AppendableValue val = (AppendableValue) currentPropValues.get(0);
                String s = null;
                try {
                    s = val.retrieve();
                    state.nodeTypeName = QName.fromJCRName(s, nsContext);
                } catch (IOException ioe) {
                    throw new SAXException("error while retrieving value", ioe);
                } catch (IllegalNameException ine) {
                    throw new SAXException(new InvalidSerializedDataException("illegal node type name: " + s, ine));
                } catch (UnknownPrefixException upe) {
                    throw new SAXException(new InvalidSerializedDataException("illegal node type name: " + s, upe));
                }
            } else if (currentPropName.equals(JCR_MIXINTYPES)) {
                if (state.mixinNames == null) {
                    state.mixinNames = new ArrayList(currentPropValues.size());
                }
                for (int i = 0; i < currentPropValues.size(); i++) {
                    AppendableValue val =
                            (AppendableValue) currentPropValues.get(0);
                    String s = null;
                    try {
                        s = val.retrieve();
                        QName mixin = QName.fromJCRName(s, nsContext);
                        state.mixinNames.add(mixin);
                    } catch (IOException ioe) {
                        throw new SAXException("error while retrieving value", ioe);
                    } catch (IllegalNameException ine) {
                        throw new SAXException(new InvalidSerializedDataException("illegal mixin type name: " + s, ine));
                    } catch (UnknownPrefixException upe) {
                        throw new SAXException(new InvalidSerializedDataException("illegal mixin type name: " + s, upe));
                    }
                }
            } else if (currentPropName.equals(JCR_UUID)) {
                AppendableValue val = (AppendableValue) currentPropValues.get(0);
                try {
                    state.uuid = val.retrieve();
                } catch (IOException ioe) {
                    throw new SAXException("error while retrieving value", ioe);
                }
            } else {
                Importer.PropInfo prop = new Importer.PropInfo();
                prop.setName(currentPropName);
                prop.setType(currentPropType);
                prop.setValues((Importer.TextValue[])
                        currentPropValues.toArray(new Importer.TextValue[currentPropValues.size()]));
                state.props.add(prop);
            }
            // reset temp fields
            currentPropValues.clear();
        } else if (SysViewSAXEventGenerator.VALUE_ELEMENT.equals(localName)) {
            // sv:value element
            currentPropValues.add(currentPropValue);
            // reset temp fields
            currentPropValue = null;
        } else {
            throw new SAXException(new InvalidSerializedDataException("invalid element in system view xml document: " + localName));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void endDocument() throws SAXException {
        try {
            importer.end();
        } catch (RepositoryException re) {
            throw new SAXException(re);
        }
    }

    //--------------------------------------------------------< inner classes >
    class ImportState {
        /**
         * name of current node
         */
        QName nodeName;
        /**
         * primary type of current node
         */
        QName nodeTypeName;
        /**
         * list of mixin types of current node
         */
        ArrayList mixinNames;
        /**
         * uuid of current node
         */
        String uuid;

        /**
         * list of PropInfo instances representing properties of current node
         */
        ArrayList props = new ArrayList();

        /**
         * flag indicating whether startNode() has been called for current node
         */
        boolean started = false;
    }
}
