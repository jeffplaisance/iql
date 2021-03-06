/*
 * Copyright (C) 2014 Indeed Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package com.indeed.imhotep.web;

import com.indeed.imhotep.iql.IQLQuery;
import com.indeed.imhotep.iql.cache.QueryCache;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
* @author dwahler
*/
@Controller
public class ResultServlet {
    private static final Logger log = Logger.getLogger(ResultServlet.class);
    private final QueryCache queryCache;

    @Autowired
    public ResultServlet(QueryCache queryCache) {
        this.queryCache = queryCache;
    }

    @RequestMapping("/results/{filename:.+}")
    protected void doGet(final HttpServletResponse resp,
                         @PathVariable("filename") String filename,
                         @RequestParam(required = false) String view,
                         OutputStream outputStream) throws IOException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        final boolean csv = filename.endsWith(".csv");
        final boolean avoidFileSave = view != null;

        if (!queryCache.isFileCached(filename)) {
            resp.sendError(404);
            return;
        }

        setContentType(resp, avoidFileSave, csv, false);
        final InputStream cacheInputStream = queryCache.getInputStream(filename);
        IQLQuery.copyStream(cacheInputStream, outputStream, Integer.MAX_VALUE, false);
        outputStream.close();

    }

    static void setContentType(HttpServletResponse resp, boolean avoidFileSave, boolean csv, boolean progress) {
        if(avoidFileSave) {
            resp.setContentType("text/plain");
        } else if (csv) {
            resp.setContentType("text/csv");
            resp.setHeader("Content-Disposition", "inline; filename=\"iqlresult.csv\"");
        } else if (progress) {
            resp.setContentType("text/event-stream");
            resp.setHeader("Cache-Control", "no-cache");
        } else {
            resp.setContentType("text/tab-separated-values");
            resp.setHeader("Content-Disposition", "inline; filename=\"iqlresult.tsv\"");
        }
    }
}
