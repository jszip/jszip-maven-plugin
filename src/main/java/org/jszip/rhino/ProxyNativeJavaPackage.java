/*
 * Copyright 2011-2012 Stephen Connolly.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jszip.rhino;

import org.mozilla.javascript.NativeJavaPackage;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * A proxy for a real {@link NativeJavaPackage} that allows modifications to be made.
 */
public class ProxyNativeJavaPackage extends ScriptableObject implements Serializable {
    static final long serialVersionUID = 1L;

    protected final NativeJavaPackage delegate;
    private final Map<String, Object> mutations = new HashMap<String, Object>();

    public ProxyNativeJavaPackage(NativeJavaPackage delegate) {
        delegate.getClass();
        this.delegate = delegate;
    }

    @Override
    public String getClassName() {
        return delegate.getClassName();
    }

    @Override
    public boolean has(String id, Scriptable start) {
        return mutations.containsKey(id) ? mutations.get(id) != null : delegate.has(id, start);
    }

    @Override
    public boolean has(int index, Scriptable start) {
        return delegate.has(index, start);
    }

    @Override
    public void put(String id, Scriptable start, Object value) {
        mutations.put(id, value);
    }

    @Override
    public void put(int index, Scriptable start, Object value) {
        delegate.put(index, start, value);
    }

    @Override
    public Object get(String id, Scriptable start) {
        if (mutations.containsKey(id)) {
            return mutations.get(id);
        }
        return delegate.get(id, start);
    }

    @Override
    public Object get(int index, Scriptable start) {
        return delegate.get(index, start);
    }

    @Override
    public Object getDefaultValue(Class<?> ignored) {
        return toString();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ProxyNativeJavaPackage) {
            ProxyNativeJavaPackage that = (ProxyNativeJavaPackage) obj;
            return delegate.equals(that.delegate) && mutations.equals(that.mutations);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
