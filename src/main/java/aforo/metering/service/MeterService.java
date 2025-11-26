package aforo.metering.service;

import aforo.metering.dto.MeterRequest;
import aforo.metering.dto.MeterResponse;

public interface MeterService {
    MeterResponse estimate(MeterRequest request);
}
