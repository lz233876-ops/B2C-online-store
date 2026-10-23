package com.shopping.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "品牌列表项")
public class BrandVO {

    @Schema(description = "品牌ID")
    private Long id;

    @Schema(description = "品牌英文名")
    private String name;

    @Schema(description = "品牌中文名")
    private String nameCn;

    @Schema(description = "logo URL")
    private String logo;

    @Schema(description = "首字母")
    private String firstLetter;

    @Schema(description = "品牌介绍")
    private String description;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
