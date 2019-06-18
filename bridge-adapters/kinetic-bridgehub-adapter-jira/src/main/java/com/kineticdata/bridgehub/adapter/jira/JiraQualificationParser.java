package com.kineticdata.bridgehub.adapter.jira;

import com.kineticdata.bridgehub.adapter.QualificationParser;

/**
 *
 */
public class JiraQualificationParser extends QualificationParser {
    public String encodeParameter(String name, String value) {
        return value;
    }
}
