package com.afriserve.smsmanager.data.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.afriserve.smsmanager.models.Recipient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class ExcelParserTest {

    @Test
    public void parseWithSmartMapping_detectsNormalizedPhoneNumberHeaders() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("FullNames", "Kiprono Laban Koros");
        row.put("Normalized Phone Number", "254796338979");
        row.put("Amount Disbursed", "Ksh 5,000.00");
        row.put("Branch", "MOLO");

        ExcelParser.ParseResult result = ExcelParser.parseWithSmartMapping(List.of(row));

        assertEquals("FullNames", result.mapping.name);
        assertEquals("Normalized Phone Number", result.mapping.phone);
        assertEquals("Amount Disbursed", result.mapping.amount);
        assertEquals(1, result.recipients.size());

        Recipient recipient = result.recipients.get(0);
        assertEquals("Kiprono Laban Koros", recipient.getName());
        assertEquals("+254796338979", recipient.getPhone());
        assertNotNull(recipient.getAmount());
        assertEquals(5000.0, recipient.getAmount(), 0.001);
    }
}
