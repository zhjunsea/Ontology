    @Test
    @DisplayName("第四十轮·卷之四成篇照录回归（8 组全录）")
    void round40JuanSiLanding() throws Exception {
        assertThat((String) field("JUAN_SI_ZI_HUA_ZHI_MI_XU")).as("JUAN_SI_ZI_HUA_ZHI_MI_XU")
                .startsWith("字画指迷夫先天者，已露之机；后天").contains("神，精微入圣。自古结绳为政，如今")
                .endsWith("而相之。略说根源，以示后学。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_XUAN_HUANG")).as("JUAN_SI_XUAN_HUANG")
                .startsWith("玄黄克应歌玄者，天也；黄者，地也").contains("。玄黄叙龟形未判，此为太古之淳风")
                .endsWith("止须勤记取，灾祥议论觉风生。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_FU")).as("JUAN_SI_FU")
                .startsWith("花押赋夫押字者，人之心印也。古人").contains("他人。霜叶辞柯，飘零不由自己。画")
                .endsWith("疑于预见，后之学者幸珍重之。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_ZI_HUA_JING_YAN")).as("JUAN_SI_ZI_HUA_JING_YAN")
                .startsWith("字画经验敷字：昔在任宰，请折之，").contains("无祖产，当知有五成。小人生不足，")
                .endsWith("坑陷并尖缺，妻子骄奢保守难。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_XIANG_ZI_XIN_YI")).as("JUAN_SI_XIANG_ZI_XIN_YI")
                .startsWith("相字心易凡写两字，止看一字。盖字").contains("，荣显通达。笔画气势，慷慨意志。")
                .endsWith("体骨苏黄格，淡有精神是贵郎。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_DUAN_FA")).as("JUAN_SI_DUAN_FA")
                .startsWith("视势每遇人写来，必别是何字，如“").contains("亦心也。一寸乃十分，为人有十分之")
                .endsWith("阳人不可写阴字。皆反常故也。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_LIU_SHEN")).as("JUAN_SI_LIU_SHEN")
                .startsWith("六神笔法“乀”：青龙木。“乂”：").contains("蛇土力隔伏玄黄笔法歌厂、反——反")
                .endsWith("声。旺事宜重用，取谋合有成。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_SI_WU_XING")).as("JUAN_SI_WU_XING")
                .startsWith("五行体格式水笔式水圆多性巧。浊者").contains("假如庐字撇长，则取为丙火、巳火用")
                .endsWith("”，取江河在下义也。余仿此。").doesNotContain("「").doesNotContain("」");
    }

    @Test
    @DisplayName("第四十一轮·卷之五成篇照录回归（14 组全录）")
    void round41JuanWuLanding() throws Exception {
        assertThat((String) field("JUAN_WU_WU_XING_QUAN_BEI")).as("JUAN_WU_WU_XING_QUAN_BEI")
                .startsWith("拆字杂编五行全备一点一画五行全，").contains("、丿“尖短交加朱雀神”。撇短而有")
                .endsWith("八卦知凶吉，学者参求理自全。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_SHEN_SHA")).as("JUAN_WU_SHEN_SHA")
                .startsWith("贵神中上贝日月大人喜神士口言鸟福").contains("所属是科名，如甲乙以一直，丙人以")
                .endsWith("主，详其旺相休囚以定吉凶。）").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_QI_YAN_ZUO_YONG_GE")).as("JUAN_WU_QI_YAN_ZUO_YONG_GE")
                .startsWith("七言作用歌用神加直五行真，谋望营").contains("木逢金克，缠身病疾不明萌。又木形")
                .endsWith("若是为官客，几日新鲜一旦倾。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_BI_LI_GE_XI_JIANG_YUE")).as("JUAN_WU_BI_LI_GE_XI_JIANG_YUE")
                .startsWith("比例歌一斗日来占事不差，无心书鬼").contains("定其间事必徐。问失执金知是铁，始")
                .endsWith("逢王者世为奇，君免猜疑直示。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_ZHAN_LI")).as("JUAN_WU_ZHAN_LI")
                .startsWith("易理玄微昔李淳风占赤黑二马入河，").contains("曰：“自今十四日必来。”刘曰：“")
                .endsWith("者富。五行生克亦然。余仿此。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_BIAO")).as("JUAN_WU_BIAO")
                .startsWith("五行四时旺相休囚表五行相生地支木").contains("蒙需讼师，比小畜兮履泰否。同人大")
                .endsWith("生。问官官不谐，见财财不成。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_SI_YAN_DU_BU")).as("JUAN_WU_SI_YAN_DU_BU")
                .startsWith("四言独步看字之法，毫不可差。下笔").contains("蓬。金得炉锤，方成器皿。木无金制")
                .endsWith("非一日。仔细详占，万无一失。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_WU_YAN_ZUO_YONG_GE")).as("JUAN_WU_WU_YAN_ZUO_YONG_GE")
                .startsWith("五言作用歌断事不可泥，变通方是道").contains("舌凶。用神见腾蛇，俱是文书动。功")
                .endsWith("神。先定吉凶主，然后字中寻。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_BIE_LI_PIAN")).as("JUAN_WU_BIE_LI_PIAN")
                .startsWith("别理篇字义浑论，辨别之篇须下学。").contains("西南北，欲就其方。左右中前，乃择")
                .endsWith("关悠远，须认精粗为化造之原。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_LIU_YAN_POU_DUAN_GE")).as("JUAN_WU_LIU_YAN_POU_DUAN_GE")
                .startsWith("六言剖断歌事从天地之义，字乃圣贤").contains("精神，英雄定于骨格。末后一笔丰隆")
                .endsWith("学者变化细推，断事无不灵应。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_GE_WU_ZHANG")).as("JUAN_WU_GE_WU_ZHANG")
                .startsWith("格物章物格而后知致，本末须详。事").contains("来，来而复去。文章书籍，非小人用")
                .endsWith("其重焉，非其道焉，孰轻与尔？").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_WU_LI_LUN")).as("JUAN_WU_WU_LI_LUN")
                .startsWith("物理论三才始判，八卦攸分。万物不").contains("社稷自有人求，关津诚为客阻。烟雾")
                .endsWith("我听。”旨哉斯言！胡可忽诸。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_WU_XING_LIU_SHEN_LUN")).as("JUAN_WU_WU_XING_LIU_SHEN_LUN")
                .startsWith("五行六神辨别论先以五行为主，次向").contains("有勾陈，难结案头文。见朱雀，忖量")
                .endsWith("发。密搜奥义规绳，不爽纤微。").doesNotContain("「").doesNotContain("」");
        assertThat((String) field("JUAN_WU_JIN_SHENG_ZHANG")).as("JUAN_WU_JIN_SHENG_ZHANG")
                .startsWith("金声章混沌未开，一元含于太极。无").contains("叱之即便去；鸡鱼鹅鸭，欲用则不生")
                .endsWith("欲造字相之微，请明章中之理。").doesNotContain("「").doesNotContain("」");
    }
