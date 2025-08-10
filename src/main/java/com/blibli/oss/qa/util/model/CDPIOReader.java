package com.blibli.oss.qa.util.model;

import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v137.io.IO;
import org.openqa.selenium.devtools.v137.io.model.StreamHandle;

import java.util.Base64;
import java.util.Iterator;
import java.util.Optional;

public class CDPIOReader implements Iterator<CDPIOReader.ResponseChunk> {
    private final DevTools client;
    private final StreamHandle stream;
    private final int chunkSize;
    private Status status = Status.NOT_STARTED;

    private enum Status {
        NOT_STARTED, IN_PROGRESS, COMPLETED
    }

    public static class ResponseChunk {
        private final byte[] data;

        public ResponseChunk(byte[] data) {
            this.data = data;
        }

        public byte[] get() {
            return data;
        }
    }

    public CDPIOReader(DevTools client, StreamHandle stream, int chunkSize) {
        this.client = client;
        this.stream = stream;
        this.chunkSize = chunkSize;
    }

    private byte[] read(int size) {
        if (status == Status.COMPLETED) {
            return new byte[]{};
        } else if (status == Status.NOT_STARTED) {
            status = Status.IN_PROGRESS;
        }

        var response = client.send(
                IO.read(
                        stream,
                        Optional.empty(), Optional.of(size)
                )
        );
        if (response.getEof()) {
            this.status = Status.COMPLETED;
            client.send(IO.close(stream));
        }
        return response.getBase64Encoded().orElse(false)
                ?
                Base64.getDecoder().decode(response.getData())
                :
                response.getData().getBytes();
    }

    @Override
    public boolean hasNext() {
        return status != Status.COMPLETED;
    }

    @Override
    public ResponseChunk next() {
        return new ResponseChunk(read(chunkSize));
    }
}
