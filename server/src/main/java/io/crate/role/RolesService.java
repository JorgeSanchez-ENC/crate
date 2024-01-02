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
 */

package io.crate.role;

import static io.crate.role.PrivilegeState.DENY;
import static io.crate.role.PrivilegeState.GRANT;
import static io.crate.role.PrivilegeState.REVOKE;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.jetbrains.annotations.Nullable;

import io.crate.common.FourFunction;
import io.crate.role.metadata.RolesMetadata;
import io.crate.role.metadata.UsersMetadata;
import io.crate.role.metadata.UsersPrivilegesMetadata;

public class RolesService implements Roles, ClusterStateListener {

    private volatile Map<String, Role> roles = Map.of(Role.CRATE_USER.name(), Role.CRATE_USER);

    @Inject
    public RolesService(ClusterService clusterService) {
        clusterService.addListener(this);
    }

    @Override
    public Collection<Role> roles() {
        return roles.values();
    }

    @Nullable
    @Override
    public Role findUser(String userName) {
        Role role = roles.get(userName);
        if (role != null && role.isUser()) {
            return role;
        }
        return null;
    }

    @Override
    @Nullable
    public Role findRole(String roleName) {
        return roles.get(roleName);
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        Metadata prevMetadata = event.previousState().metadata();
        Metadata newMetadata = event.state().metadata();

        UsersMetadata prevUsers = prevMetadata.custom(UsersMetadata.TYPE);
        UsersMetadata newUsers = newMetadata.custom(UsersMetadata.TYPE);
        RolesMetadata prevRoles = prevMetadata.custom(RolesMetadata.TYPE);
        RolesMetadata newRoles = newMetadata.custom(RolesMetadata.TYPE);

        UsersPrivilegesMetadata prevUsersPrivileges = prevMetadata.custom(UsersPrivilegesMetadata.TYPE);
        UsersPrivilegesMetadata newUsersPrivileges = newMetadata.custom(UsersPrivilegesMetadata.TYPE);

        if (prevUsers != newUsers || prevRoles != newRoles || prevUsersPrivileges != newUsersPrivileges) {
            roles = getRoles(newUsers, newRoles, newUsersPrivileges);
        }
    }


    static Map<String, Role> getRoles(@Nullable UsersMetadata usersMetadata,
                                      @Nullable RolesMetadata rolesMetadata,
                                      @Nullable UsersPrivilegesMetadata privilegesMetadata) {
        Map<String, Role> roles = new HashMap<>();
        roles.put(Role.CRATE_USER.name(), Role.CRATE_USER);
        if (usersMetadata != null) {
            for (Map.Entry<String, SecureHash> user: usersMetadata.users().entrySet()) {
                String userName = user.getKey();
                Set<Privilege> privileges = Set.of();
                if (privilegesMetadata != null) {
                    var oldPrivileges = privilegesMetadata.getUserPrivileges(userName);
                    if (oldPrivileges != null) {
                        privileges = oldPrivileges;
                    }
                }
                roles.put(userName, new Role(userName, true, privileges, Set.of(), user.getValue()));
            }
        } else if (rolesMetadata != null) {
            roles.putAll(rolesMetadata.roles());
        }
        return Collections.unmodifiableMap(roles);
    }

    @Override
    public boolean hasPrivilege(Role user, Privilege.Type type, Privilege.Clazz clazz, @Nullable String ident) {
        return hasPrivilege(user, type, clazz, ident, HAS_PRIVILEGE_FUNCTION) == GRANT;
    }

    @Override
    public boolean hasAnyPrivilege(Role user, Privilege.Clazz clazz, @Nullable String ident) {
        return hasPrivilege(user, null, clazz, ident, HAS_ANY_PRIVILEGE_FUNCTION) == GRANT;
    }

    @Override
    public boolean hasSchemaPrivilege(Role user, Privilege.Type type, Integer schemaOid) {
        return hasPrivilege(user, type, null, schemaOid, HAS_SCHEMA_PRIVILEGE_FUNCTION) == GRANT;
    }

    /**
     * Resolves privilege recursively in a depth-first fashion.
     * DENY has precedence, so given a role, if for one of its parents the privilege resolves to DENY,
     * then the privilege resolves to DENY for the role.
     */
    private PrivilegeState hasPrivilege(
        Role role,
        Privilege.Type type,
        @Nullable Privilege.Clazz clazz,
        @Nullable Object object,
        FourFunction<Role, Privilege.Type, Privilege.Clazz, Object, PrivilegeState> function) {

        if (role.isSuperUser()) {
            return GRANT;
        }
        PrivilegeState resolution = function.apply(role, type, clazz, object);
        if (resolution == DENY || resolution == GRANT) {
            return resolution;
        }


        PrivilegeState result = REVOKE;
        for (String parentRoleName : role.grantedRoleNames()) {
            var parentRole = findRole(parentRoleName);
            assert parentRole != null : "role must exist";
            var partialResult = hasPrivilege(parentRole, type, clazz, object, function);
            if (partialResult == DENY) {
                return DENY;
            }
            if (result == REVOKE) {
                result = partialResult;
            }
        }
        return result;
    }
}
