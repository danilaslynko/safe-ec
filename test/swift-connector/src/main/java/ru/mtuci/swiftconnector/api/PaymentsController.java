package ru.mtuci.swiftconnector.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.mtuci.swiftconnector.dto.CustomerCredit;
import ru.mtuci.swiftconnector.dto.PaymentIdentification;
import ru.mtuci.swiftconnector.dto.StatusMessage;
import ru.mtuci.swiftconnector.service.swift.Pacs008Generator;
import ru.mtuci.swiftconnector.service.swift.SwiftService;

@RestController
@RequestMapping("/api/v1.0")
@RequiredArgsConstructor
public class PaymentsController
{
    private final Pacs008Generator pacs008Generator;
    private final SwiftService swiftService;
    private final ObjectMapper mapper;

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/customer-credit")
    public void sendPayment(@RequestBody(required = false) CustomerCredit customerCredit)
    {
        var xml = pacs008Generator.generate(customerCredit);
        swiftService.sendMessage(xml);
    }

    @GetMapping("/customer-credit/status")
    public StatusMessage getStatus(@RequestParam(name = "endToEndId") String endToEndId,
                                   @RequestParam(name = "transactionId") String transactionId,
                                   @RequestParam(name = "uetr") String uetr)
    {
        var pmtId = new PaymentIdentification(endToEndId, transactionId, uetr);
        var status = swiftService.getStatus(pmtId);
        if (status != null)
            return status;

        return new StatusMessage(pmtId, "unknown", "");
    }
}
