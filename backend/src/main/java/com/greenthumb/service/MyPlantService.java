package com.greenthumb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenthumb.common.BizException;
import com.greenthumb.common.ErrorCode;
import com.greenthumb.dto.CreatePlantRequest;
import com.greenthumb.entity.PlantSpeciesEntity;
import com.greenthumb.entity.UserPlantEntity;
import com.greenthumb.mapper.PlantSpeciesMapper;
import com.greenthumb.mapper.UserPlantMapper;
import com.greenthumb.vo.MyPlantDetailVO;
import com.greenthumb.vo.MyPlantVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 我的植物。
 * <p>
 * 查询策略：先按 user_id 查 user_plant，再按 speciesId 集合批量查 plant_species，
 * 在内存里拼装。避免 N+1 查询，也不需要写自定义 SQL。
 * <p>
 * ★ 数据隔离：所有方法第一件事就是用 user_id 过滤或校验归属。
 *   这是所有业务接口的模板 —— 绝不能允许 A 用户读到/改到 B 用户的数据。
 */
@Service
public class MyPlantService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final UserPlantMapper userPlantMapper;
    private final PlantSpeciesMapper plantSpeciesMapper;

    public MyPlantService(UserPlantMapper userPlantMapper, PlantSpeciesMapper plantSpeciesMapper) {
        this.userPlantMapper = userPlantMapper;
        this.plantSpeciesMapper = plantSpeciesMapper;
    }

    /* ================= 列表 ================= */

    public List<MyPlantVO> list(Long userId, String healthStatus) {
        LambdaQueryWrapper<UserPlantEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPlantEntity::getUserId, userId);
        if (healthStatus != null && !healthStatus.isBlank()) {
            wrapper.eq(UserPlantEntity::getHealthStatus, healthStatus);
        }
        wrapper.orderByDesc(UserPlantEntity::getCreatedAt);

        List<UserPlantEntity> plants = userPlantMapper.selectList(wrapper);
        if (plants.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, PlantSpeciesEntity> speciesMap = loadSpecies(plants);
        return plants.stream().map(p -> toVO(p, speciesMap.get(p.getSpeciesId()))).toList();
    }

    /* ================= 详情 ================= */

    public MyPlantDetailVO detail(Long userId, Long plantId) {
        UserPlantEntity plant = getOwned(userId, plantId);
        PlantSpeciesEntity species = speciesOf(plant.getSpeciesId());
        return MyPlantDetailVO.from(toVO(plant, species));
    }

    /* ================= 新增 ================= */

    @Transactional
    public MyPlantVO create(Long userId, CreatePlantRequest req) {
        if (req == null || req.speciesId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请选择植物种类");
        }
        PlantSpeciesEntity species = plantSpeciesMapper.selectById(req.speciesId());
        if (species == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "植物种类不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        UserPlantEntity p = new UserPlantEntity();
        p.setUserId(userId);
        p.setSpeciesId(req.speciesId());
        p.setNickname(blankToNull(req.nickname()) != null
                ? req.nickname().trim()
                : species.getCommonName());   // 没填昵称就用物种名，避免列表里显示空白
        p.setLocation(blankToNull(req.location()));
        p.setLightLevel(blankToNull(req.lightLevel()));
        p.setPotSize(blankToNull(req.potSize()));
        p.setPotMaterial(blankToNull(req.potMaterial()));
        // 用户没填土壤就取物种的推荐值
        p.setSoilType(blankToNull(req.soilType()) != null ? req.soilType().trim() : species.getSoilType());
        p.setAcquiredDate(parseDate(req.acquiredDate()));
        p.setHealthStatus("HEALTHY");
        p.setAddSource("MANUAL");
        p.setNote(blankToNull(req.note()));
        p.setDeleted(0);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        userPlantMapper.insert(p);

        return toVO(p, species);
    }

    /* ================= 内部 ================= */

    /** 取一盆植物并校验归属，防止越权访问别人的植物 */
    public UserPlantEntity getOwned(Long userId, Long plantId) {
        if (plantId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少植物 id");
        }
        UserPlantEntity plant = userPlantMapper.selectById(plantId);
        if (plant == null || !Objects.equals(plant.getUserId(), userId)) {
            // 故意返回「不存在」而不是「无权限」，避免暴露别人植物的 id 是否存在
            throw new BizException(ErrorCode.NOT_FOUND, "植物不存在");
        }
        return plant;
    }

    public PlantSpeciesEntity speciesOf(Long speciesId) {
        return speciesId == null ? null : plantSpeciesMapper.selectById(speciesId);
    }

    private Map<Long, PlantSpeciesEntity> loadSpecies(List<UserPlantEntity> plants) {
        Set<Long> ids = plants.stream()
                .map(UserPlantEntity::getSpeciesId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return plantSpeciesMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PlantSpeciesEntity::getId, Function.identity(), (a, b) -> a));
    }

    public MyPlantVO toVO(UserPlantEntity p, PlantSpeciesEntity s) {
        return new MyPlantVO(
                p.getId(),
                p.getSpeciesId(),
                s == null ? null : s.getCommonName(),
                s == null ? null : s.getEmoji(),
                p.getNickname(),
                p.getLocation(),
                p.getLightLevel(),
                p.getPotSize(),
                p.getPotMaterial(),
                p.getSoilType(),
                formatDate(p.getAcquiredDate()),
                p.getHealthStatus(),
                p.getAddSource(),
                p.getNote(),
                formatDateTime(p.getCreatedAt()),
                null,   // lastWateredAt   —— 养护日志功能完成后填充
                null    // nextWaterDueDate —— 养护计划功能完成后填充
        );
    }

    private static String formatDate(LocalDate d) {
        return d == null ? null : d.format(DATE);
    }

    private static String formatDateTime(LocalDateTime t) {
        return t == null ? null : t.format(ISO);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
