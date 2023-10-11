package ru.mtuci.swiftconnector.service.swift;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.mtuci.swiftconnector.ExchangeConfig;
import ru.mtuci.swiftconnector.dto.PaymentIdentification;
import ru.mtuci.swiftconnector.dto.StatusMessage;
import ru.mtuci.swiftconnector.service.crypto.XmlSignService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class SwiftService
{
    private static final String OUT = "out";
    private static final String IN = "in";

    private final Map<PaymentIdentification, StatusMessage> statuses = new ConcurrentHashMap<>();

    private final XmlSignService xmlSignService;
    private final Pacs002Component pacs002Component;
    private final ExchangeConfig exchangeConfig;

    @SneakyThrows
    public void sendMessage(String dataPdu)
    {
        var type = getType(dataPdu);
        var id = StringUtils.substringBetween(dataPdu, "<BizMsgIdr>", "</BizMsgIdr>");
        var filename = "%s-%s-%s.mx".formatted(type, id, System.nanoTime());
        var signed = xmlSignService.signXml(dataPdu);
        var outDir = Path.of(exchangeConfig.getMessagesDir(), OUT);
        var outPath = outDir.resolve(filename);
        if (!Files.exists(outDir))
            Files.createDirectories(outDir);

        Files.writeString(outPath, signed);
        log.info("Message written to {}", outPath.toAbsolutePath());
    }

    private static String getType(String dataPdu)
    {
        return StringUtils.substringBetween(dataPdu, "<MsgDefIdr>", "</MsgDefIdr>").trim();
    }

    public StatusMessage getStatus(PaymentIdentification pmtId)
    {
        var statusMessage = statuses.get(pmtId);
        if (statusMessage != null)
            return statusMessage;

        var inDir = Path.of(exchangeConfig.getMessagesDir(), IN);
        if (!Files.exists(inDir))
            return null;

        try (var list = Files.list(inDir))
        {
            var files = list.toList();
            for (Path file : files)
            {
                if (!Files.isRegularFile(file))
                    continue;

                var message = Files.readString(file);
                if (!StringUtils.startsWith(getType(message), "pacs.002"))
                    continue;

                try
                {
                    log.info("Read message\n{}", message);
                    xmlSignService.verifySignature(message);
                    var parsed = pacs002Component.parse(message);
                    statuses.put(parsed.originalIdentification(), parsed);
                }
                catch (Exception e)
                {
                    log.error("Unable to parse message", e);
                }
                finally
                {
                    Files.deleteIfExists(file);
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        return statuses.get(pmtId);
    }
}
