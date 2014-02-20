/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.operator.scalar;

import com.google.common.collect.ImmutableList;
import io.crate.metadata.FunctionIdent;
import io.crate.metadata.FunctionInfo;
import io.crate.metadata.Scalar;
import io.crate.operator.Input;
import io.crate.planner.symbol.Function;
import io.crate.planner.symbol.Symbol;
import org.cratedb.DataType;

import java.util.Set;

public class CollectionCountFunction implements Scalar<Long, Set<DataType>> {

    public static final String NAME = "collection_count";
    private final FunctionInfo info;

    public static void register(ScalarFunctionModule mod) {
        for (DataType t : DataType.SET_TYPES) {
            mod.registerScalarFunction(
                    new CollectionCountFunction(
                            new FunctionInfo(new FunctionIdent(NAME, ImmutableList.of(t)), DataType.LONG))
            );
        }
    }

    public CollectionCountFunction(FunctionInfo info) {
        this.info = info;
    }

    @Override
    public Long evaluate(Input<Set<DataType>>... args) {
        // TODO: eliminate Integer.MAX_VALUE limitation of Set.size()
        return new Long((args[0].value()).size());
    }

    @Override
    public FunctionInfo info() {
        return info;
    }

    @Override
    public Symbol normalizeSymbol(Function function) {
        return function;
    }
}
