package com.dronecontrol.region;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegionService {

	@Autowired
	private RegionMapper regionMapper;

	public Region createRegion(Region region) {
		int result = regionMapper.insertRegion(region);
		return (result > 0) ? region : null;
	}

	public List<Region> listRegions(Integer parentId) {
		if (parentId == null) {
			return regionMapper.selectTopLevelRegions();
		}
		return regionMapper.selectRegionsByParentId(parentId);
	}

	public Region getRegion(int id) {
		return regionMapper.selectRegionById(id);
	}

	public Region updateRegion(int id, Region patch) {
		Region existing = regionMapper.selectRegionById(id);
		if (existing == null) {
			return null;
		}

		if (patch.getName() != null) {
			existing.setName(patch.getName());
		}

		int result = regionMapper.updateRegion(existing);
		return (result > 0) ? existing : null;
	}

}
