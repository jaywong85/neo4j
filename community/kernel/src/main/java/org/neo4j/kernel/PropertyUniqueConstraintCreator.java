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
package org.neo4j.kernel;

import org.neo4j.graphdb.ConstraintViolationException;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.schema.ConstraintCreator;
import org.neo4j.graphdb.schema.ConstraintDefinition;
import org.neo4j.kernel.api.exceptions.KernelException;

public class PropertyUniqueConstraintCreator extends PropertyConstraintCreator
{
    PropertyUniqueConstraintCreator( InternalSchemaActions actions, Label label, String propertyKeyOrNull )
    {
        super( actions, label, propertyKeyOrNull );
    }

    @Override
    public ConstraintCreator unique()
    {
        throw new IllegalStateException( "Already unique" );
    }

    @Override
    protected ConstraintCreator doOn( String propertyKey )
    {
        return new PropertyUniqueConstraintCreator( actions, label, propertyKey );
    }

    @Override
    protected ConstraintDefinition doCreate()
    {
        assertInTransaction();

        try
        {
            return actions.createPropertyUniquenessConstraint( label, propertyKey );
        }
        catch ( KernelException e )
        {
            String userMessage = actions.getUserMessage( e );
            throw new ConstraintViolationException( userMessage, e );
        }
    }
}
