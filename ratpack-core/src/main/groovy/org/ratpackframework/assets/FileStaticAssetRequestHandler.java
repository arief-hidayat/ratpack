/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ratpackframework.assets;

import org.ratpackframework.util.HttpDateUtil;
import org.vertx.java.core.Handler;
import org.vertx.java.core.file.FileProps;

public class FileStaticAssetRequestHandler implements Handler<StaticAssetRequest> {

  @Override
  public void handle(final StaticAssetRequest assetRequest) {
    assetRequest.exists(new Handler<Boolean>() {
      @Override
      public void handle(Boolean exists) {
        if (exists) {
          assetRequest.props(new Handler<FileProps>() {
            @Override
            public void handle(FileProps props) {
              assetRequest.getRequest().response.putHeader("Last-Modified", HttpDateUtil.formatDate(props.lastModifiedTime));
              assetRequest.getRequest().response.sendFile(assetRequest.getFilePath());
            }
          });
        } else {
          assetRequest.getNotFoundHandler().handle(assetRequest.getRequest());
        }
      }
    });
  }
}