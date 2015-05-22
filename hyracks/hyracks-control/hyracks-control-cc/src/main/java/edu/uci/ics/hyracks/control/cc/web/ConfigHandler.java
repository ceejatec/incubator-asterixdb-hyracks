/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.control.cc.web;

import edu.uci.ics.hyracks.control.cc.web.util.IINIOutputFunction;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.ini4j.Ini;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ConfigHandler extends AbstractHandler {
    public ConfigHandler() {
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        while (target.startsWith("/")) {
            target = target.substring(1);
        }
        while (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1);
        }
        String[] parts = target.split("/");
        try {
            Ini ini = new Ini();
            ini.add("nc");
            Ini.Section nc = ini.get("nc");
            nc.add("ccHost", "localhost");
            nc.add("ccPort", "7744");
            response.setContentType("text/plain");
            ini.store(response.getWriter());
            baseRequest.setHandled(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}