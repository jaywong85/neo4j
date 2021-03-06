/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphdb;

import java.util.Set;

import org.junit.Rule;
import org.junit.Test;

import org.neo4j.test.DatabaseRule;
import org.neo4j.test.ImpermanentDatabaseRule;
import org.neo4j.tooling.GlobalGraphOperations;

import static org.junit.Assert.assertEquals;

import static org.neo4j.helpers.collection.IteratorUtil.asSet;

public class LabelScanStoreIT
{
    @Test
    public void shouldGetNodesWithCreatedLabel() throws Exception
    {
        // GIVEN
        Node node1 = createLabeledNode( Labels.First );
        Node node2 = createLabeledNode( Labels.Second );
        Node node3 = createLabeledNode( Labels.Third );
        Node node4 = createLabeledNode( Labels.First, Labels.Second, Labels.Third );
        Node node5 = createLabeledNode( Labels.First, Labels.Third );
        
        // THEN
        assertEquals(
                asSet( node1, node4, node5 ),
                asSet( getAllNodesWithLabel( Labels.First ) ) );
        assertEquals(
                asSet( node2, node4 ),
                asSet( getAllNodesWithLabel( Labels.Second ) ) );
        assertEquals(
                asSet( node3, node4, node5 ),
                asSet( getAllNodesWithLabel( Labels.Third ) ) );
    }
    
    @Test
    public void shouldGetNodesWithAddedLabel() throws Exception
    {
        // GIVEN
        Node node1 = createLabeledNode( Labels.First );
        Node node2 = createLabeledNode( Labels.Second );
        Node node3 = createLabeledNode( Labels.Third );
        Node node4 = createLabeledNode( Labels.First );
        Node node5 = createLabeledNode( Labels.First );
        
        // WHEN
        addLabels( node4, Labels.Second, Labels.Third );
        addLabels( node5, Labels.Third );
        
        // THEN
        assertEquals(
                asSet( node1, node4, node5 ),
                asSet( getAllNodesWithLabel( Labels.First ) ) );
        assertEquals(
                asSet( node2, node4 ),
                asSet( getAllNodesWithLabel( Labels.Second ) ) );
        assertEquals(
                asSet( node3, node4, node5 ),
                asSet( getAllNodesWithLabel( Labels.Third ) ) );
    }
    
    @Test
    public void shouldGetNodesAfterDeletedNodes() throws Exception
    {
        // GIVEN
        Node node1 = createLabeledNode( Labels.First, Labels.Second );
        Node node2 = createLabeledNode( Labels.First, Labels.Third );
        
        // WHEN
        deleteNode( node1 );
        
        // THEN
        assertEquals(
                asSet( node2 ),
                getAllNodesWithLabel( Labels.First ) );
        assertEquals(
                asSet(),
                getAllNodesWithLabel( Labels.Second ) );
        assertEquals(
                asSet( node2 ),
                getAllNodesWithLabel( Labels.Third ) );
    }
    
    @Test
    public void shouldGetNodesAfterRemovedLabels() throws Exception
    {
        // GIVEN
        Node node1 = createLabeledNode( Labels.First, Labels.Second );
        Node node2 = createLabeledNode( Labels.First, Labels.Third );
        
        // WHEN
        removeLabels( node1, Labels.First );
        removeLabels( node2, Labels.Third );
        
        // THEN
        assertEquals(
                asSet( node2 ),
                getAllNodesWithLabel( Labels.First ) );
        assertEquals(
                asSet( node1 ),
                getAllNodesWithLabel( Labels.Second ) );
        assertEquals(
                asSet(),
                getAllNodesWithLabel( Labels.Third ) );
    }
    
    private void removeLabels( Node node, Label... labels )
    {
        Transaction tx = dbRule.getGraphDatabaseService().beginTx();
        try
        {
            for ( Label label : labels )
            {
                node.removeLabel( label );
            }
            tx.success();
        }
        finally
        {
            tx.finish();
        }
    }

    private void deleteNode( Node node )
    {
        Transaction tx = dbRule.getGraphDatabaseService().beginTx();
        try
        {
            node.delete();
            tx.success();
        }
        finally
        {
            tx.finish();
        }
    }

    private Set<Node> getAllNodesWithLabel( Label label )
    {
        Transaction tx = dbRule.getGraphDatabaseService().beginTx();
        try
        {
            return asSet( GlobalGraphOperations.at( dbRule.getGraphDatabaseService() ).getAllNodesWithLabel( label ) );
        }
        finally
        {
            tx.finish();
        }
    }

    private Node createLabeledNode( Label... labels )
    {
        Transaction tx = dbRule.getGraphDatabaseService().beginTx();
        try
        {
            Node node = dbRule.getGraphDatabaseService().createNode( labels );
            tx.success();
            return node;
        }
        finally
        {
            tx.finish();
        }
    }
    
    private void addLabels( Node node, Label... labels )
    {
        Transaction tx = dbRule.getGraphDatabaseService().beginTx();
        try
        {
            for ( Label label : labels )
            {
                node.addLabel( label );
            }
            tx.success();
        }
        finally
        {
            tx.finish();
        }
    }

    public final @Rule DatabaseRule dbRule = new ImpermanentDatabaseRule();
    
    private static enum Labels implements Label
    {
        First,
        Second,
        Third;
    }
}
