package com.dronecontrol.history;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkHistoryService {

	@Autowired
	private WorkHistoryMapper workHistoryMapper;

	public void log(int adminId, String actionType, String target, String detail) {
		WorkHistory history = new WorkHistory();
		history.setAdminId(adminId);
		history.setActionType(actionType);
		history.setTarget(target);
		history.setDetail(detail);
		workHistoryMapper.insert(history);
	}

	public List<WorkHistory> list(Integer adminId, String actionType) {
		return workHistoryMapper.select(adminId, actionType);
	}

}
