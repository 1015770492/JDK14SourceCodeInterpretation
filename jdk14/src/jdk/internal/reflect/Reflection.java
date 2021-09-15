/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package jdk.internal.reflect;

import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.misc.VM;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 总的来说这个类是jdk内部调用的，不提供开发者人员使用
 */

public class Reflection {

    /**
     * 过滤类中敏感的字段和方法
     * 写时复制进行同步
     */
    // 过滤字段
    private static volatile Map<Class<?>, Set<String>> fieldFilterMap;
    // 过滤方法
    private static volatile Map<Class<?>, Set<String>> methodFilterMap;

    private static final String WILDCARD = "*";
    public static final Set<String> ALL_MEMBERS = Set.of(WILDCARD);

    static {
        fieldFilterMap = Map.of(
                Reflection.class, ALL_MEMBERS,
                AccessibleObject.class, ALL_MEMBERS,
                Class.class, Set.of("classLoader"),
                ClassLoader.class, ALL_MEMBERS,
                Constructor.class, ALL_MEMBERS,
                Field.class, ALL_MEMBERS,
                Method.class, ALL_MEMBERS,
                Module.class, ALL_MEMBERS,
                System.class, Set.of("security")
        );
        methodFilterMap = Map.of();
    }

    /**
     * 返回调用者的Class类
     * 会忽略 java.lang.reflect.Method.invoke()栈帧的调用。（会忽略代理类找到内部那个类）
     */
    @CallerSensitive
    @HotSpotIntrinsicCandidate
    public static native Class<?> getCallerClass();

    /**
     * 兼容性原因，它用于代替 Class.getModifiers() 进行运行时访问检查；
     * 参阅 4471811。只有低 13 位的值（即 0x1FFF 的掩码）才能保证有效。
     */
    @HotSpotIntrinsicCandidate
    public static native int getClassAccessFlags(Class<?> c);


    /**
     * 确保拥有访问成员的权限（校验权限）
     * 当前类、成员类、目标类
     */
    public static void ensureMemberAccess(Class<?> currentClass, Class<?> memberClass, Class<?> targetClass, int modifiers) throws IllegalAccessException {
        // 校验权限
        if (!verifyMemberAccess(currentClass, memberClass, targetClass, modifiers)) {
            throw newIllegalAccessException(currentClass, memberClass, targetClass, modifiers);
        }
    }

    /**
     * 校验成员属性的访问权限是否相同(public、private、protect、default)
     * 相同则返回true
     * modifiers 参数的作用是手动传入(public、private、protect、default) 对应的int值表示所拥有的访问权限
     */
    public static boolean verifyMemberAccess(Class<?> currentClass, Class<?> memberClass, Class<?> targetClass, int modifiers) {
        Objects.requireNonNull(currentClass);
        Objects.requireNonNull(memberClass);
        // 校验是不是同一个class
        if (currentClass == memberClass) {
            return true;
        }
        // 校验有没有包访问权限
        if (!verifyModuleAccess(currentClass.getModule(), memberClass)) {
            return false;
        }

        boolean gotIsSameClassPackage = false;
        boolean isSameClassPackage = false;

        // 校验是否拥有public访问权限
        if (!Modifier.isPublic(getClassAccessFlags(memberClass))) {
            // 如果不是public还需要判断是不是同一个包下（通过校验是否使用的是同一个类加载器来判断）
            isSameClassPackage = isSameClassPackage(currentClass, memberClass);
            gotIsSameClassPackage = true;
            if (!isSameClassPackage) {
                // 不是同一个类加载器加载的类，故校验返回失败（虽然是同名，包路径也相似，处于安全考虑因此返回校验失败）
                // jdk拥有沙箱机制，所以如果伪造类使用的类加载器不同显然就是假的
                return false;
            }
        }

        if (Modifier.isPublic(modifiers)) {
            return true;// 拥有public访问权限
        }

        // 判断是否是private访问权限
        if (Modifier.isPrivate(modifiers)) {
            // 是否是 内部类
            if (areNestMates(currentClass, memberClass)) {
                return true;
            }
        }

        boolean successSoFar = false;

        if (Modifier.isProtected(modifiers)) {
            // See if currentClass is a subclass of memberClass
            if (isSubclassOf(currentClass, memberClass)) {
                successSoFar = true;
            }
        }

        if (!successSoFar && !Modifier.isPrivate(modifiers)) {
            if (!gotIsSameClassPackage) {
                isSameClassPackage = isSameClassPackage(currentClass,
                        memberClass);
                gotIsSameClassPackage = true;
            }

            if (isSameClassPackage) {
                successSoFar = true;
            }
        }

        if (!successSoFar) {
            return false;
        }

        if (targetClass != null && Modifier.isProtected(modifiers) &&
                targetClass != currentClass) {
            if (!gotIsSameClassPackage) {
                isSameClassPackage = isSameClassPackage(currentClass, memberClass);
                gotIsSameClassPackage = true;
            }
            if (!isSameClassPackage) {
                if (!isSubclassOf(targetClass, currentClass)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 判断memberClass是否是public类型的，是则返回true
     */
    public static boolean verifyPublicMemberAccess(Class<?> memberClass, int modifiers) {
        Module m = memberClass.getModule();
        return Modifier.isPublic(modifiers)
                && m.isExported(memberClass.getPackageName())
                && Modifier.isPublic(Reflection.getClassAccessFlags(memberClass));
    }

    /**
     * 校验 当前类所在模块是否拥有访问成员类所在的模块权限，有则返回true
     */
    public static boolean verifyModuleAccess(Module currentModule, Class<?> memberClass) {
        Module memberModule = memberClass.getModule();// 获取模块
        if (currentModule == memberModule) {
            // same module (named or unnamed) or both null if called
            // before module system is initialized, which means we are
            // dealing with java.base only.
            return true;
        } else {
            String pkg = memberClass.getPackageName();
            return memberModule.isExported(pkg, currentModule);
        }
    }

    /**
     * 判断是否是同一个包下
     */
    private static boolean isSameClassPackage(Class<?> c1, Class<?> c2) {
        if (c1.getClassLoader() != c2.getClassLoader())
            return false;
        return Objects.equals(c1.getPackageName(), c2.getPackageName());
    }

    /**
     * 判断 queryClass 是否是 ofClass 的子类，实际上用instanceof就可以了
     */
    static boolean isSubclassOf(Class<?> queryClass, Class<?> ofClass) {
        while (queryClass != null) {
            if (queryClass == ofClass) {
                return true;
            }
            queryClass = queryClass.getSuperclass();
        }
        return false;
    }

    // fieldNames must contain only interned Strings
    public static synchronized void registerFieldsToFilter(Class<?> containingClass, Set<String> fieldNames) {
        fieldFilterMap = registerFilter(fieldFilterMap, containingClass, fieldNames);
    }

    // methodNames must contain only interned Strings
    public static synchronized void registerMethodsToFilter(Class<?> containingClass, Set<String> methodNames) {
        methodFilterMap = registerFilter(methodFilterMap, containingClass, methodNames);
    }

    private static Map<Class<?>, Set<String>> registerFilter(Map<Class<?>, Set<String>> map, Class<?> containingClass, Set<String> names) {
        if (map.get(containingClass) != null) {
            throw new IllegalArgumentException
                    ("Filter already registered: " + containingClass);
        }
        map = new HashMap<>(map);
        map.put(containingClass, Set.copyOf(names));
        return map;
    }

    public static Field[] filterFields(Class<?> containingClass, Field[] fields) {
        if (fieldFilterMap == null) {
            // Bootstrapping
            return fields;
        }
        return (Field[]) filter(fields, fieldFilterMap.get(containingClass));
    }

    public static Method[] filterMethods(Class<?> containingClass, Method[] methods) {
        if (methodFilterMap == null) {
            // Bootstrapping
            return methods;
        }
        return (Method[]) filter(methods, methodFilterMap.get(containingClass));
    }

    private static Member[] filter(Member[] members, Set<String> filteredNames) {
        if ((filteredNames == null) || (members.length == 0)) {
            return members;
        }
        Class<?> memberType = members[0].getClass();
        if (filteredNames.contains(WILDCARD)) {
            return (Member[]) Array.newInstance(memberType, 0);
        }
        int numNewMembers = 0;
        for (Member member : members) {
            if (!filteredNames.contains(member.getName())) {
                ++numNewMembers;
            }
        }
        Member[] newMembers = (Member[]) Array.newInstance(memberType, numNewMembers);
        int destIdx = 0;
        for (Member member : members) {
            if (!filteredNames.contains(member.getName())) {
                newMembers[destIdx++] = member;
            }
        }
        return newMembers;
    }

    /**
     * Tests if the given method is caller-sensitive and the declaring class
     * is defined by either the bootstrap class loader or platform class loader.
     */
    public static boolean isCallerSensitive(Method m) {
        final ClassLoader loader = m.getDeclaringClass().getClassLoader();
        if (VM.isSystemDomainLoader(loader)) {
            return m.isAnnotationPresent(CallerSensitive.class);
        }
        return false;
    }

    /**
     * 根据权限校验被拒绝，返回一个 IllegalAccessException消息
     */
    public static IllegalAccessException newIllegalAccessException(Class<?> currentClass,
                                                                   Class<?> memberClass,
                                                                   Class<?> targetClass,
                                                                   int modifiers) {
        if (currentClass == null)
            return newIllegalAccessException(memberClass, modifiers);

        String currentSuffix = "";
        String memberSuffix = "";
        Module m1 = currentClass.getModule();
        if (m1.isNamed())
            currentSuffix = " (in " + m1 + ")";
        Module m2 = memberClass.getModule();
        if (m2.isNamed())
            memberSuffix = " (in " + m2 + ")";

        String memberPackageName = memberClass.getPackageName();

        String msg = currentClass + currentSuffix + " cannot access ";
        if (m2.isExported(memberPackageName, m1)) {

            // module access okay so include the modifiers in the message
            msg += "a member of " + memberClass + memberSuffix +
                    " with modifiers \"" + Modifier.toString(modifiers) + "\"";

        } else {
            // module access failed
            msg += memberClass + memberSuffix + " because "
                    + m2 + " does not export " + memberPackageName;
            if (m2.isNamed()) msg += " to " + m1;
        }

        return new IllegalAccessException(msg);
    }

    /**
     * 依据没有访问掉框架返回一个 IllegalAccessException
     */
    private static IllegalAccessException newIllegalAccessException(Class<?> memberClass,
                                                                    int modifiers) {
        String memberSuffix = "";
        Module m2 = memberClass.getModule();
        if (m2.isNamed())
            memberSuffix = " (in " + m2 + ")";

        String memberPackageName = memberClass.getPackageName();

        String msg = "JNI attached native thread (null caller frame) cannot access ";
        if (m2.isExported(memberPackageName)) {

            // module access okay so include the modifiers in the message
            msg += "a member of " + memberClass + memberSuffix +
                    " with modifiers \"" + Modifier.toString(modifiers) + "\"";

        } else {
            // module access failed
            msg += memberClass + memberSuffix + " because "
                    + m2 + " does not export " + memberPackageName;
        }

        return new IllegalAccessException(msg);
    }

    /**
     * 判断是不是静态内部类
     */
    public static native boolean areNestMates(Class<?> currentClass,
                                              Class<?> memberClass);
}
