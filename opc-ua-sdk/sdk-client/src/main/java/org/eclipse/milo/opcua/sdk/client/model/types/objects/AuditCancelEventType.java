/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.milo.opcua.sdk.client.model.types.objects;

import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.client.model.types.variables.PropertyType;
import org.eclipse.milo.opcua.sdk.core.QualifiedProperty;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;

public interface AuditCancelEventType extends AuditSessionEventType {
    QualifiedProperty<UInteger> REQUEST_HANDLE = new QualifiedProperty<>(
        "http://opcfoundation.org/UA/",
        "RequestHandle",
        ExpandedNodeId.parse("nsu=http://opcfoundation.org/UA/;i=7"),
        ValueRanks.Scalar,
        UInteger.class
    );

    CompletableFuture<? extends PropertyType> getRequestHandleNode();

    CompletableFuture<UInteger> getRequestHandle();

    CompletableFuture<StatusCode> setRequestHandle(UInteger value);
}
