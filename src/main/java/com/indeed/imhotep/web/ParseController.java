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

import com.google.common.base.Strings;
import com.indeed.imhotep.sql.ast2.IQLStatement;
import com.indeed.imhotep.sql.ast2.SelectStatement;
import com.indeed.imhotep.sql.parser.StatementParser;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author vladimir
 */

@Controller
public class ParseController {
    private static final Logger log = Logger.getLogger(ParseController.class);

    private ImhotepMetadataCache metadata;

    @Autowired
    public ParseController(ImhotepMetadataCache metadata) {
        this.metadata = metadata;
    }

    @RequestMapping("/parse")
    @ResponseBody
    protected SelectStatement handleParse(@RequestParam("q") String query,
                                          @RequestParam(value = "json", required = false, defaultValue = "") String json,
                                          HttpServletResponse resp) throws IOException {

        resp.setHeader("Access-Control-Allow-Origin", "*");
        try {
            final IQLStatement parsedQuery = StatementParser.parse(query, metadata);
            if(!(parsedQuery instanceof SelectStatement)) {
                throw new RuntimeException("The query is not recognized as a select statement: " + query);
            }
            return (SelectStatement) parsedQuery;
        } catch (Throwable e) {
            QueryServlet.handleError(resp, !Strings.isNullOrEmpty(json), e, false, false);
            return null;
        }
    }
}
