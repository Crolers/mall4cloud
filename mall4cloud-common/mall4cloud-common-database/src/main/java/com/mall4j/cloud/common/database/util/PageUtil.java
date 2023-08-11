package com.mall4j.cloud.common.database.util;

import com.github.pagehelper.ISelect;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageSerializable;
import com.mall4j.cloud.common.database.dto.PageDTO;
import com.mall4j.cloud.common.database.vo.PageVO;

/**
 * @author FrozenWatermelon
 * @date 2020/9/8
 */
public class PageUtil {

    /**
     * 使用pagehelper进行分页，该分页只能一对一
     */
    public static <T> PageVO<T> doPage(PageDTO pageDTO, ISelect select) {

        // 在你需要进行分页的 MyBatis 查询方法前调用 PageHelper.startPage 静态方法即可，紧跟在这个方法后的第一个MyBatis 查询方法会被进行分页。
        PageSerializable<T> simplePageInfo = PageHelper.startPage(pageDTO).doSelectPageSerializable(select);

        PageVO<T> pageVO = new PageVO<>();
        pageVO.setList(simplePageInfo.getList());
        pageVO.setTotal(simplePageInfo.getTotal());
        pageVO.setPages(getPages(simplePageInfo.getTotal(), pageDTO.getPageSize()));
        return pageVO;
    }



    public static Integer getPages(long total, Integer pageSize) {

        if (total == -1) {
            return 1;
        }
        if (pageSize > 0) {
            return  (int) (total / pageSize + ((total % pageSize == 0) ? 0 : 1));
        }
        return  0;
    }
}
