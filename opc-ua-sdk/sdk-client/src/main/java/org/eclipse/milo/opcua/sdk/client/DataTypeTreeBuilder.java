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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.eclipse.milo.opcua.stack.client.UaStackClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.NamespaceTable;
import org.eclipse.milo.opcua.stack.core.serialization.UaResponseMessage;
import org.eclipse.milo.opcua.stack.core.types.OpcUaDefaultBinaryEncoding;
import org.eclipse.milo.opcua.stack.core.types.OpcUaDefaultXmlEncoding;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseNextRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseNextResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadResponse;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.RequestHeader;
import org.eclipse.milo.opcua.stack.core.types.structured.ViewDescription;
import org.eclipse.milo.opcua.stack.core.util.FutureUtils;
import org.eclipse.milo.opcua.stack.core.util.Tree;
import org.eclipse.milo.opcua.stack.core.util.Unit;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * Builds a {@link DataTypeTree} by recursively browsing the DataType hierarchy starting at
 * {@link Identifiers#BaseDataType}.
 */
public final class DataTypeTreeBuilder {

    private DataTypeTreeBuilder() {}

    /**
     * Build a {@link DataTypeTree} by recursively browsing the DataType hierarchy starting at
     * {@link Identifiers#BaseDataType}.
     *
     * @param client  a connected {@link UaStackClient}.
     * @param session an active {@link OpcUaSession}.
     * @return a {@link DataTypeTree}.
     */
    public static CompletableFuture<DataTypeTree> build(UaStackClient client, OpcUaSession session) {
        Tree<DataTypeTree.DataType> root = new Tree<>(
            null,
            new DataTypeTree.DataType(
                QualifiedName.parse("0:BaseDataType"), Identifiers.BaseDataType,
                null,
                null
            )
        );

        return readNamespaceTable(client, session)
            .thenCompose(namespaceTable -> addChildren(root, client, session, namespaceTable))
            .thenApply(u -> new DataTypeTree(root));
    }

    private static CompletableFuture<NamespaceTable> readNamespaceTable(UaStackClient client, OpcUaSession session) {
        RequestHeader requestHeader = client.newRequestHeader(
            session.getAuthenticationToken(),
            client.getConfig().getRequestTimeout()
        );

        CompletableFuture<UaResponseMessage> readFuture = client.sendRequest(
            new ReadRequest(
                requestHeader,
                0.0,
                TimestampsToReturn.Neither,
                new ReadValueId[]{
                    new ReadValueId(
                        Identifiers.Server_NamespaceArray,
                        AttributeId.Value.uid(),
                        null,
                        QualifiedName.NULL_VALUE)}
            )
        );

        return readFuture.thenApply(ReadResponse.class::cast).thenApply(response -> {
            DataValue dataValue = response.getResults()[0];
            String[] namespaceUris = (String[]) dataValue.getValue().getValue();
            NamespaceTable namespaceTable = new NamespaceTable();
            for (String namespaceUri : namespaceUris) {
                namespaceTable.addUri(namespaceUri);
            }
            return namespaceTable;
        });
    }

    private static CompletableFuture<Unit> addChildren(
        Tree<DataTypeTree.DataType> tree,
        UaStackClient client,
        OpcUaSession session,
        NamespaceTable namespaceTable
    ) {

        CompletableFuture<List<ReferenceDescription>> subtypes = browseSafe(
            client,
            session,
            new BrowseDescription(
                tree.getValue().getNodeId(),
                BrowseDirection.Forward,
                Identifiers.HasSubtype,
                false,
                uint(NodeClass.DataType.getValue()),
                uint(BrowseResultMask.All.getValue())
            )
        );

        CompletableFuture<List<DataTypeTree.DataType>> dataTypesFuture = subtypes.thenCompose(references -> {
            Stream<CompletableFuture<DataTypeTree.DataType>> dataTypeFutures =
                references.stream().map(dataTypeReference -> {
                    NodeId dataTypeId = dataTypeReference.getNodeId()
                        .local(namespaceTable)
                        .orElse(NodeId.NULL_VALUE);

                    CompletableFuture<List<ReferenceDescription>> encodings = browseSafe(
                        client,
                        session,
                        new BrowseDescription(
                            dataTypeId,
                            BrowseDirection.Forward,
                            Identifiers.HasEncoding,
                            false,
                            uint(NodeClass.Object.getValue()),
                            uint(BrowseResultMask.All.getValue())
                        )
                    );

                    return encodings.thenApply(encodingReferences -> {
                        NodeId binaryEncodingId = null;
                        NodeId xmlEncodingId = null;

                        for (ReferenceDescription r : encodingReferences) {
                            if (r.getBrowseName().equals(OpcUaDefaultBinaryEncoding.ENCODING_NAME)) {
                                binaryEncodingId = r.getNodeId().local(namespaceTable).orElse(null);
                            } else if (r.getBrowseName().equals(OpcUaDefaultXmlEncoding.ENCODING_NAME)) {
                                xmlEncodingId = r.getNodeId().local(namespaceTable).orElse(null);
                            }
                        }

                        return new DataTypeTree.DataType(
                            dataTypeReference.getBrowseName(),
                            dataTypeId,
                            binaryEncodingId,
                            xmlEncodingId
                        );
                    });
                });

            return FutureUtils.sequence(dataTypeFutures);
        });

        return dataTypesFuture
            .thenCompose(dataTypes -> {
                Stream<CompletableFuture<Unit>> futures = dataTypes.stream()
                    .map(tree::addChild)
                    .map(childNode -> addChildren(childNode, client, session, namespaceTable));

                return FutureUtils.sequence(futures);
            })
            .thenApply(v -> Unit.VALUE);
    }

    /**
     * Browse a {@link BrowseDescription} "safely", completing successfully
     * with an empty List if any exceptions occur.
     *
     * @param client            a {@link UaStackClient}.
     * @param session           an {@link OpcUaSession}.
     * @param browseDescription the {@link BrowseDescription}.
     * @return a List of {@link ReferenceDescription}s obtained by browsing {@code browseDescription}.
     */
    private static CompletableFuture<List<ReferenceDescription>> browseSafe(
        UaStackClient client,
        OpcUaSession session,
        BrowseDescription browseDescription
    ) {

        return browse(client, session, browseDescription)
            .exceptionally(ex -> Collections.emptyList());
    }

    private static CompletableFuture<List<ReferenceDescription>> browse(
        UaStackClient client,
        OpcUaSession session,
        BrowseDescription browseDescription
    ) {

        BrowseRequest browseRequest = new BrowseRequest(
            client.newRequestHeader(
                session.getAuthenticationToken(),
                client.getConfig().getRequestTimeout()
            ),
            new ViewDescription(
                NodeId.NULL_VALUE,
                DateTime.MIN_VALUE,
                uint(0)
            ),
            uint(0),
            new BrowseDescription[]{browseDescription}
        );

        return client.sendRequest(browseRequest).thenApply(BrowseResponse.class::cast).thenCompose(response -> {
            BrowseResult result = response.getResults()[0];

            List<ReferenceDescription> references =
                Collections.synchronizedList(new ArrayList<>());

            return maybeBrowseNext(client, session, references, result);
        });
    }

    private static CompletableFuture<List<ReferenceDescription>> maybeBrowseNext(
        UaStackClient client,
        OpcUaSession session,
        List<ReferenceDescription> references,
        BrowseResult result
    ) {

        if (result.getStatusCode().isGood()) {
            Collections.addAll(references, result.getReferences());

            ByteString nextContinuationPoint = result.getContinuationPoint();

            if (nextContinuationPoint == null || nextContinuationPoint.isNull()) {
                return CompletableFuture.completedFuture(references);
            } else {
                return browseNext(client, session, nextContinuationPoint, references);
            }
        } else {
            return CompletableFuture.completedFuture(references);
        }
    }

    private static CompletableFuture<List<ReferenceDescription>> browseNext(
        UaStackClient client,
        OpcUaSession session,
        ByteString continuationPoint,
        List<ReferenceDescription> references
    ) {

        BrowseNextRequest browseNextRequest = new BrowseNextRequest(
            client.newRequestHeader(
                session.getAuthenticationToken(),
                client.getConfig().getRequestTimeout()
            ),
            false,
            new ByteString[]{continuationPoint}
        );

        return client.sendRequest(browseNextRequest).thenApply(BrowseNextResponse.class::cast).thenCompose(response -> {
            BrowseResult result = response.getResults()[0];

            return maybeBrowseNext(client, session, references, result);
        });
    }

}
