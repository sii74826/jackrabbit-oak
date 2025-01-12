/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.security.authorization.composite;

import java.util.Arrays;
import java.util.Set;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.plugins.tree.TreeLocation;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.Permissions;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.RepositoryPermission;
import org.apache.jackrabbit.oak.spi.security.authorization.permission.TreePermission;
import org.apache.jackrabbit.oak.spi.security.privilege.PrivilegeConstants;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Test implementation of the {@code AggregatedPermissionProvider} with following
 * characteristics:
 *
 * This provider supports all permissions
 * but only grants {@link org.apache.jackrabbit.oak.spi.security.authorization.permission.Permissions#NAMESPACE_MANAGEMENT} on repository level
 * and {@link org.apache.jackrabbit.oak.spi.security.authorization.permission.Permissions#READ_NODE} on regular items.
 *
 * In this case the provider will always be respected for evaluation and will
 * therefore cause the final result to be always restricted to the permissions
 * granted by this provider.
 *
 * NOTE: this provider implementation doesn't properly filter out access
 * control content for which {@link Permissions#READ_ACCESS_CONTROL} must be
 * enforced. this has been omitted here for the simplicity of the test.
 */
class FullScopeProvider extends AbstractAggrProvider implements PrivilegeConstants {

    FullScopeProvider(@NotNull Root root) {
        super(root);
    }

    //-------------------------------------------------< PermissionProvider >---

    @NotNull
    @Override
    public Set<String> getPrivileges(@Nullable Tree tree) {
        if (tree == null) {
            return Set.of(JCR_NAMESPACE_MANAGEMENT);
        } else {
            return Set.of(REP_READ_NODES);
        }
    }

    @Override
    public boolean hasPrivileges(@Nullable Tree tree, @NotNull String... privilegeNames) {
        if (tree == null) {
            return Arrays.equals(new String[]{JCR_NAMESPACE_MANAGEMENT}, privilegeNames);
        } else {
            return Arrays.equals(new String[]{REP_READ_NODES}, privilegeNames);
        }
    }

    @NotNull
    @Override
    public RepositoryPermission getRepositoryPermission() {
        return new RepositoryPermission() {
            @Override
            public boolean isGranted(long repositoryPermissions) {
                return Permissions.NAMESPACE_MANAGEMENT == repositoryPermissions;
            }
        };
    }

    @NotNull
    @Override
    public TreePermission getTreePermission(@NotNull Tree tree, @NotNull TreePermission parentPermission) {
        return new TestTreePermission(tree.getPath());
    }

    @Override
    public boolean isGranted(@NotNull Tree tree, @Nullable PropertyState property, long permissions) {
        return property == null && permissions == Permissions.READ_NODE;
    }

    @Override
    public boolean isGranted(@NotNull String oakPath, @NotNull String jcrActions) {
        Tree tree = root.getTree(oakPath);
        return tree.exists() && Session.ACTION_READ.equals(jcrActions);
    }

    //---------------------------------------< AggregatedPermissionProvider >---

    @Override
    public boolean isGranted(@NotNull TreeLocation location, long permissions) {
        return permissions == Permissions.READ_NODE;
    }

    //--------------------------------------------------------------------------

    private final class TestTreePermission implements TreePermission {

        private final String path;

        private TestTreePermission(@NotNull String path) {
            this.path = path;
        }

        @NotNull
        @Override
        public TreePermission getChildPermission(@NotNull String childName, @NotNull NodeState childState) {
            return new TestTreePermission(PathUtils.concat(path, childName));
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public boolean canRead(@NotNull PropertyState property) {
            return false;
        }

        @Override
        public boolean canReadAll() {
            return false;
        }

        @Override
        public boolean canReadProperties() {
            return false;
        }

        @Override
        public boolean isGranted(long permissions) {
            return Permissions.READ_NODE == permissions;
        }

        @Override
        public boolean isGranted(long permissions, @NotNull PropertyState property) {
            return false;
        }
    }
}
