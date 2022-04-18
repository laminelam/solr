/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.common.cloud.acl;

import java.util.List;

public interface ZkCredentialsInjector {

  List<ZkCredential> getZkCredentials();

  class ZkCredential {

    public enum Perms {
      all,
      read
    }

    private String username;
    private String password;
    private String perms;

    public ZkCredential() {}

    public ZkCredential(String username, String password, Perms perms) {
      this(username, password, String.valueOf(perms));
    }

    public ZkCredential(String username, String password, String perms) {
      this.username = username;
      this.password = password;
      this.perms = perms;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public String getPerms() {
      return perms;
    }

    public boolean isAll() {
      return Perms.all.toString().equalsIgnoreCase(perms);
    }

    public boolean isReadonly() {
      return Perms.read.toString().equalsIgnoreCase(perms);
    }

    @Override
    public String toString() {
      return "ZkCredential{"
          + "username='"
          + username
          + '\''
          + ", password=[hidden]"
          + ", perms='"
          + perms
          + '\''
          + '}';
    }
  }
}
