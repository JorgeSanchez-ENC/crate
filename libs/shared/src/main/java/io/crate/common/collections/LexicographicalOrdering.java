/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
 *
 *
 * Copyright (C) 2016 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.crate.common.collections;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Iterator;

import org.jetbrains.annotations.Nullable;


public final class LexicographicalOrdering<T> extends Ordering<Iterable<T>> implements Serializable {
    final Comparator<? super T> elementOrder;

    public LexicographicalOrdering(Comparator<? super T> elementOrder) {
        this.elementOrder = elementOrder;
    }

    @Override
    public int compare(Iterable<T> leftIterable, Iterable<T> rightIterable) {
        Iterator<T> left = leftIterable.iterator();
        Iterator<T> right = rightIterable.iterator();
        while (left.hasNext()) {
            if (!right.hasNext()) {
                return LEFT_IS_GREATER; // because it's longer
            }
            int result = elementOrder.compare(left.next(), right.next());
            if (result != 0) {
                return result;
            }
        }
        if (right.hasNext()) {
            return RIGHT_IS_GREATER; // because it's longer
        }
        return 0;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (object == this) {
            return true;
        }
        if (object instanceof LexicographicalOrdering) {
            LexicographicalOrdering<?> that = (LexicographicalOrdering<?>) object;
            return this.elementOrder.equals(that.elementOrder);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return elementOrder.hashCode() ^ 2075626741; // meaningless
    }

    @Override
    public String toString() {
        return elementOrder + ".lexicographical()";
    }

    private static final long serialVersionUID = 0;
}
