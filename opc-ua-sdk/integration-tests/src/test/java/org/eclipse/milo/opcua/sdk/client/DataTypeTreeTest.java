/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.milo.opcua.sdk.client;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.eclipse.milo.opcua.stack.core.BuiltinDataType;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.util.Tree;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DataTypeTreeTest extends ClientServerTest {

    private DataTypeTree dataTypeTree;

    @BeforeAll
    public void buildDataTypeTree() throws ExecutionException, InterruptedException {
        dataTypeTree = DataTypeTreeBuilder.build(
            client.getStackClient(),
            client.getSession().get()
        ).get();
    }

    @Test
    public void testGetTree() {
        dataTypeTree.getTree().traverseWithDepth((dataType, depth) -> {
            for (int i = 0; i < depth; i++) {
                System.out.print("\t");
            }
            System.out.println(dataType.getBrowseName().toParseableString());
        });
    }

    @Test
    public void testGetBackingClass() {
        // all subtypes of String are backed by String.class
        checkSubtypes(Identifiers.String, String.class);

        // all subtypes of DateTime are backed by DateTime.class
        checkSubtypes(Identifiers.DateTime, DateTime.class);

        // all subtypes of ByteString are backed by ByteString.class
        checkSubtypes(Identifiers.ByteString, ByteString.class);

        // all subtypes of NodeId are backed by NodeId.class
        checkSubtypes(Identifiers.NodeId, NodeId.class);

        // all subtypes of Structure are backed by ExtensionObject.class
        checkSubtypes(Identifiers.Structure, ExtensionObject.class);

        // all subtypes of Double are backed by Double.class
        checkSubtypes(Identifiers.Double, Double.class);

        // all subtypes of UInt32 are backed by UInteger.class
        checkSubtypes(Identifiers.UInt32, UInteger.class);

        // all subtypes of UInt64 are backed by ULong.class
        checkSubtypes(Identifiers.UInt64, ULong.class);

        // all subtypes of Enumeration are backed by Integer.class
        checkSubtypes(Identifiers.Enumeration, Integer.class);
    }

    private void checkSubtypes(NodeId dataTypeId, Class<?> expectedBackingClass) {
        Tree<DataTypeTree.DataType> nodeIdNode = dataTypeTree.getTreeNode(dataTypeId);
        assertNotNull(nodeIdNode);
        nodeIdNode.traverse(dataType -> {
            Class<?> backingClass = dataTypeTree.getBackingClass(dataType.getNodeId());
            System.out.println(dataType.getBrowseName().toParseableString() + " : " + backingClass);
            assertEquals(expectedBackingClass, backingClass);
        });
    }

    @Test
    public void testGetBuiltinType() {
        // Check all the builtin types
        for (BuiltinDataType expectedType : BuiltinDataType.values()) {
            BuiltinDataType builtinType = dataTypeTree.getBuiltinType(expectedType.getNodeId());

            assertEquals(expectedType, builtinType);
        }

        // Check that subtypes resolve to their builtin types
        assertEquals(BuiltinDataType.String, dataTypeTree.getBuiltinType(Identifiers.NumericRange));
        assertEquals(BuiltinDataType.DateTime, dataTypeTree.getBuiltinType(Identifiers.Date));
        assertEquals(BuiltinDataType.ByteString, dataTypeTree.getBuiltinType(Identifiers.Image));
        assertEquals(BuiltinDataType.ByteString, dataTypeTree.getBuiltinType(Identifiers.ImageBMP));
        assertEquals(BuiltinDataType.NodeId, dataTypeTree.getBuiltinType(Identifiers.SessionAuthenticationToken));
        assertEquals(BuiltinDataType.ExtensionObject, dataTypeTree.getBuiltinType(Identifiers.TrustListDataType));
        assertEquals(BuiltinDataType.Double, dataTypeTree.getBuiltinType(Identifiers.Duration));
        assertEquals(BuiltinDataType.UInt32, dataTypeTree.getBuiltinType(Identifiers.IntegerId));
        assertEquals(BuiltinDataType.UInt64, dataTypeTree.getBuiltinType(Identifiers.BitFieldMaskDataType));
        // note: enumerations resolve to BaseDataType aka Variant
        assertEquals(BuiltinDataType.Variant, dataTypeTree.getBuiltinType(Identifiers.NamingRuleType));
    }

    @Test
    public void testIsAssignable() {}

    @Test
    public void testGetEncodingIds() {
        Tree<DataTypeTree.DataType> treeNode = dataTypeTree.getTreeNode(Identifiers.Structure);
        assertNotNull(treeNode);

        treeNode.traverse(dataType -> {
            if (!Objects.equals(dataType.getBrowseName().getName(), "Structure")) {
                assertNotNull(dataType.getBinaryEncodingId());
                assertNotNull(dataType.getXmlEncodingId());
            }
        });
    }

}
