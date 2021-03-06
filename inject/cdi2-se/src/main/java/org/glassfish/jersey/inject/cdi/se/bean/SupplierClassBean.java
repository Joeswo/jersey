/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.jersey.inject.cdi.se.bean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionTarget;

import org.glassfish.jersey.inject.cdi.se.ParameterizedTypeImpl;
import org.glassfish.jersey.inject.cdi.se.injector.JerseyInjectionTarget;
import org.glassfish.jersey.internal.inject.DisposableSupplier;
import org.glassfish.jersey.internal.inject.SupplierClassBinding;

/**
 * Creates an implementation of {@link javax.enterprise.inject.spi.Bean} interface using Jersey's {@link SupplierClassBinding}.
 * Binding provides the information about the bean also called {@link javax.enterprise.inject.spi.BeanAttributes} information and
 * {@link JerseyInjectionTarget} provides the contextual part of the bean because implements
 * {@link javax.enterprise.context.spi.Contextual} with Jersey injection extension (is able to inject into JAX-RS/Jersey specified
 * annotation).
 * <p>
 * Bean's implementation provides possibility to register {@link Supplier} and {@link DisposableSupplier}.
 * <p>
 * Inject example:
 * <pre>
 * AbstractBinder {
 *     &#64;Override
 *     protected void configure() {
 *         bindFactory(MyBeanSupplier.class)
 *              .to(MyBean.class)
 *              .in(Singleton.class)&#59;
 *     }
 * }
 * </pre>
 * Register example:
 * <pre>
 *  &#64;Path("/")
 *  public class MyResource {
 *    &#64;Inject
 *    private Supplier&lt;MyBean&gt; myBean&#59;
 *  }
 * </pre>
 *
 * @author Petr Bouda (petr.bouda at oracle.com)
 */
class SupplierClassBean<T> extends JerseyBean<Supplier<T>> {

    private final Set<Type> contracts = new HashSet<>();
    private final Class<? extends Supplier<T>> supplierClass;
    private final Class<? extends Annotation> supplierScope;
    private InjectionTarget<Supplier<T>> injectionTarget;

    /**
     * Creates a new Jersey-specific {@link javax.enterprise.inject.spi.Bean} instance.
     *
     * @param binding {@link javax.enterprise.inject.spi.BeanAttributes} part of the bean.
     */
    SupplierClassBean(SupplierClassBinding<T> binding) {
        super(binding);
        this.supplierClass = binding.getSupplierClass();
        this.supplierScope = binding.getSupplierScope();

        for (Type contract : binding.getContracts()) {
            this.contracts.add(new ParameterizedTypeImpl(Supplier.class, contract));
            if (DisposableSupplier.class.isAssignableFrom(supplierClass)) {
                this.contracts.add(new ParameterizedTypeImpl(DisposableSupplier.class, contract));
            }
        }
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return supplierScope == null ? Dependent.class : transformScope(supplierScope);
    }

    @Override
    public Set<Type> getTypes() {
        return contracts;
    }

    @Override
    public Supplier<T> create(CreationalContext<Supplier<T>> context) {
        Supplier<T> instance = injectionTarget.produce(context);
        injectionTarget.inject(instance, context);
        injectionTarget.postConstruct(instance);
        return instance;
    }

    @Override
    public void destroy(Supplier<T> instance, CreationalContext<Supplier<T>> context) {
        injectionTarget.preDestroy(instance);
        injectionTarget.dispose(instance);
        context.release();
    }

    @Override
    public Class<?> getBeanClass() {
        return supplierClass;
    }

    /**
     * Lazy set of an injection target because to create fully functional injection target needs already created bean.
     *
     * @param injectionTarget {@link javax.enterprise.context.spi.Contextual} information belonging to this bean.
     */
    void setInjectionTarget(InjectionTarget<Supplier<T>> injectionTarget) {
        this.injectionTarget = injectionTarget;
    }
}
